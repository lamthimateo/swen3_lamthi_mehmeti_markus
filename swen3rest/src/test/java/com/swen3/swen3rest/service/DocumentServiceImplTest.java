package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.mapper.DocumentMapperImpl;
import com.swen3.swen3rest.messaging.OcrJobPublisher;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.search.DocumentSearchClient;
import com.swen3.swen3rest.search.SearchUnavailableException;
import com.swen3.swen3rest.service.exception.InvalidRequestException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import com.swen3.swen3rest.storage.FileStorage;
import com.swen3.swen3rest.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the document business logic. Repositories, file storage, search and the OCR publisher are Mockito mocks;
 * the mapper is the real class MapStruct generated, since mapping is part of what the service returns.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentLabelRepository documentLabelRepository;

    @Mock
    private OcrJobPublisher ocrJobPublisher;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private DocumentSearchClient searchClient;

    private DocumentServiceImpl documentService;

    private static final byte[] PDF_BYTES = "%PDF-1.7".getBytes();

    @BeforeEach
    void setUp() {
        documentService = new DocumentServiceImpl(documentRepository, documentLabelRepository, ocrJobPublisher,
                new DocumentMapperImpl(), fileStorage, searchClient);
    }

    private static InputStream pdf() {
        return new ByteArrayInputStream(PDF_BYTES);
    }

    // the database assigns the id on insert; the service needs it for the storage key
    private void saveAssignsId(long id) {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document saved = inv.getArgument(0);
            saved.setId(id);
            return saved;
        });
    }

    @Test
    void uploadSavesMetadata_storesTheFile_thenPublishesOcrJob(CapturedOutput output) {
        saveAssignsId(7L);

        DocumentDto dto = documentService.upload("invoice.pdf", 8, "application/pdf", pdf());

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.filename()).isEqualTo("invoice.pdf");
        assertThat(dto.uploadDate()).isNotNull();
        assertThat(dto.storagePath()).isEqualTo("7/invoice.pdf");
        assertThat(dto.ocrText()).isNull();

        // the key needs the generated id, and the job should only be queued once the file is stored
        ArgumentCaptor<Document> published = ArgumentCaptor.forClass(Document.class);
        InOrder order = inOrder(documentRepository, fileStorage, ocrJobPublisher);
        order.verify(documentRepository).save(any(Document.class));
        order.verify(fileStorage).store(eq("7/invoice.pdf"), any(InputStream.class), eq(8L), eq("application/pdf"));
        order.verify(ocrJobPublisher).publish(published.capture());
        assertThat(published.getValue().getId()).isEqualTo(7L);
        assertThat(published.getValue().getStoragePath()).isEqualTo("7/invoice.pdf");

        assertThat(output).contains("Uploaded document 7 ('invoice.pdf', 8 bytes) to '7/invoice.pdf'");
    }

    @Test
    void uploadPassesStorageFailuresOn_andDoesNotQueueAJob() {
        saveAssignsId(7L);
        StorageException storageDown = new StorageException("could not store '7/invoice.pdf' in MinIO", new RuntimeException());
        doThrow(storageDown).when(fileStorage).store(anyString(), any(InputStream.class), anyLong(), any());

        // in the running app @Transactional then rolls the insert back
        assertThatThrownBy(() -> documentService.upload("invoice.pdf", 8, "application/pdf", pdf()))
                .isSameAs(storageDown);
        verifyNoInteractions(ocrJobPublisher);
    }

    @Test
    void uploadPassesBrokerFailuresOn_andRemovesTheStoredFile() {
        saveAssignsId(7L);
        AmqpConnectException brokerDown = new AmqpConnectException(new ConnectException("Connection refused"));
        doThrow(brokerDown).when(ocrJobPublisher).publish(any(Document.class));

        // in the running app @Transactional then rolls the insert back; the file must not stay behind without a document
        assertThatThrownBy(() -> documentService.upload("invoice.pdf", 8, "application/pdf", pdf()))
                .isSameAs(brokerDown);
        verify(fileStorage).deleteQuietly("7/invoice.pdf");
    }

    @Test
    void findAllMapsThePageToDtos() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "uploadDate"));
        when(documentRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(document(1L, "invoice.pdf")), pageable, 41));

        PageResponse<DocumentDto> page = documentService.findAll(pageable);

        assertThat(page.content()).extracting(DocumentDto::filename).containsExactly("invoice.pdf");
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(41);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void findAllRejectsUnknownSortFieldWithoutQuerying() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("ocrText"));

        assertThatThrownBy(() -> documentService.findAll(pageable))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("cannot sort by 'ocrText', allowed: [id, filename, uploadDate]");
        verifyNoInteractions(documentRepository);
    }

    @Test
    void findByIdReturnsTheMappedDocument() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document(1L, "invoice.pdf")));

        assertThat(documentService.findById(1L).filename()).isEqualTo("invoice.pdf");
    }

    @Test
    void findByIdThrowsNotFoundForUnknownId() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("document 99 not found");
    }

    @Test
    void deleteRemovesLabelLinksBeforeTheDocument() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document(1L, "invoice.pdf")));

        documentService.delete(1L);

        // links first, otherwise the foreign key from document_labels blocks the delete
        InOrder order = inOrder(documentLabelRepository, documentRepository);
        order.verify(documentLabelRepository).deleteByDocumentId(1L);
        order.verify(documentRepository).deleteById(1L);
    }

    @Test
    void deleteAlsoRemovesTheStoredFile() {
        Document stored = document(1L, "invoice.pdf");
        stored.setStoragePath("1/invoice.pdf");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(stored));

        documentService.delete(1L);

        verify(fileStorage).deleteQuietly("1/invoice.pdf");
    }

    @Test
    void deleteWithoutStoredFileLeavesStorageAlone() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document(1L, "old-upload.pdf")));

        documentService.delete(1L);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void deleteMissingDocumentThrowsAndDeletesNothing() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(documentLabelRepository, never()).deleteByDocumentId(anyLong());
        verify(documentRepository, never()).deleteById(anyLong());
        verifyNoInteractions(fileStorage);
    }

    @Test
    void searchReturnsMatchingDocumentsInTheOrderOfTheHits(CapturedOutput output) {
        when(searchClient.findDocumentIds("hello", 50)).thenReturn(List.of(3L, 1L));
        when(documentRepository.findAllById(List.of(3L, 1L)))
                .thenReturn(List.of(document(1L, "first.pdf"), document(3L, "third.pdf")));

        List<DocumentDto> results = documentService.search("  hello ");

        assertThat(results).extracting(DocumentDto::filename).containsExactly("third.pdf", "first.pdf");
        assertThat(output).contains("Search 'hello' found 2 document(s)");
    }

    @Test
    void searchDropsHitsForDocumentsThatWereDeleted() {
        when(searchClient.findDocumentIds("hello", 50)).thenReturn(List.of(5L, 1L));
        when(documentRepository.findAllById(List.of(5L, 1L))).thenReturn(List.of(document(1L, "first.pdf")));

        assertThat(documentService.search("hello")).extracting(DocumentDto::id).containsExactly(1L);
    }

    @Test
    void searchWithoutHitsReturnsEmptyList() {
        when(searchClient.findDocumentIds("nothing", 50)).thenReturn(List.of());
        when(documentRepository.findAllById(List.of())).thenReturn(List.of());

        assertThat(documentService.search("nothing")).isEmpty();
    }

    @Test
    void searchRejectsBlankTermWithoutAskingTheIndex() {
        assertThatThrownBy(() -> documentService.search("   "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("search term must not be empty");
        verifyNoInteractions(searchClient, documentRepository);
    }

    @Test
    void searchPassesIndexFailuresOn() {
        SearchUnavailableException down = new SearchUnavailableException("could not reach Elasticsearch");
        when(searchClient.findDocumentIds("hello", 50)).thenThrow(down);

        assertThatThrownBy(() -> documentService.search("hello")).isSameAs(down);
    }

    private static Document document(Long id, String filename) {
        Document document = new Document();
        document.setId(id);
        document.setFilename(filename);
        document.setUploadDate(LocalDateTime.of(2026, 10, 1, 12, 0));
        return document;
    }
}
