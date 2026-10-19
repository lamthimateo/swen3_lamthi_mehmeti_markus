package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.mapper.DocumentMapperImpl;
import com.swen3.swen3rest.messaging.OcrJobPublisher;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.service.exception.InvalidRequestException;
import com.swen3.swen3rest.service.exception.NotFoundException;
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

import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the document business logic. Repositories and the OCR publisher are Mockito mocks;
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

    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentServiceImpl(
                documentRepository, documentLabelRepository, ocrJobPublisher, new DocumentMapperImpl());
    }

    @Test
    void uploadSavesMetadataThenPublishesOcrJob(CapturedOutput output) {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        DocumentDto dto = documentService.upload("invoice.pdf", 8);

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.filename()).isEqualTo("invoice.pdf");
        assertThat(dto.uploadDate()).isNotNull();
        assertThat(dto.ocrText()).isNull();

        // the job needs the generated id, so it can only be published after the insert
        ArgumentCaptor<Document> published = ArgumentCaptor.forClass(Document.class);
        InOrder order = inOrder(documentRepository, ocrJobPublisher);
        order.verify(documentRepository).save(any(Document.class));
        order.verify(ocrJobPublisher).publish(published.capture());
        assertThat(published.getValue().getId()).isEqualTo(7L);

        assertThat(output).contains("Uploaded document 7 ('invoice.pdf', 8 bytes)");
    }

    @Test
    void uploadPassesBrokerFailuresOn() {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        AmqpConnectException brokerDown = new AmqpConnectException(new ConnectException("Connection refused"));
        doThrow(brokerDown).when(ocrJobPublisher).publish(any(Document.class));

        // in the running app @Transactional then rolls the insert back
        assertThatThrownBy(() -> documentService.upload("invoice.pdf", 8)).isSameAs(brokerDown);
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
        when(documentRepository.existsById(1L)).thenReturn(true);

        documentService.delete(1L);

        // links first, otherwise the foreign key from document_labels blocks the delete
        InOrder order = inOrder(documentLabelRepository, documentRepository);
        order.verify(documentLabelRepository).deleteByDocumentId(1L);
        order.verify(documentRepository).deleteById(1L);
    }

    @Test
    void deleteMissingDocumentThrowsAndDeletesNothing() {
        when(documentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> documentService.delete(99L)).isInstanceOf(NotFoundException.class);
        verify(documentLabelRepository, never()).deleteByDocumentId(anyLong());
        verify(documentRepository, never()).deleteById(anyLong());
    }

    private static Document document(Long id, String filename) {
        Document document = new Document();
        document.setId(id);
        document.setFilename(filename);
        document.setUploadDate(LocalDateTime.of(2026, 10, 1, 12, 0));
        return document;
    }
}
