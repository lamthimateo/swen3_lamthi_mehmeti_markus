package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.messaging.OcrJobPublisher;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for DocumentController: upload, pagination of GET /api/documents, delete, and CORS.
 *
 * @WebMvcTest starts only the MVC layer (controller, CorsConfig, JSON, Pageable
 * resolver) without a database; the repository is replaced by a Mockito mock.
 */
@WebMvcTest(DocumentController.class)
@ExtendWith(OutputCaptureExtension.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentRepository documentRepository;

    @MockitoBean
    private DocumentLabelRepository documentLabelRepository;

    @MockitoBean
    private OcrJobPublisher ocrJobPublisher;

    @BeforeEach
    void setUp() {
        Document document = new Document();
        document.setId(1L);
        document.setFilename("invoice.pdf");
        document.setUploadDate(LocalDateTime.of(2026, 10, 1, 12, 0));

        // pretend the DB holds 41 documents in total and return one of them for any requested page
        when(documentRepository.findAll(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(document), inv.getArgument(0), 41));
    }

    @Test
    void uploadSavesPublishesAndLogs(CapturedOutput output) throws Exception {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF-1.7".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));

        ArgumentCaptor<Document> published = ArgumentCaptor.forClass(Document.class);
        verify(ocrJobPublisher).publish(published.capture());
        assertThat(published.getValue().getId()).isEqualTo(7L);
        assertThat(output).contains("Uploaded document 7 ('invoice.pdf', 8 bytes)");
    }

    @Test
    void uploadRejectsEmptyFileAndLogsWarning(CapturedOutput output) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest());

        verify(documentRepository, never()).save(any(Document.class));
        verify(ocrJobPublisher, never()).publish(any(Document.class));
        assertThat(output).contains("WARN").contains("Rejected upload of 'empty.pdf': file is empty");
    }

    @Test
    void listUsesDefaultPageSizeAndNewestFirst() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].filename").value("invoice.pdf"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(41))
                .andExpect(jsonPath("$.totalPages").value(3));

        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "uploadDate"));
    }

    @Test
    void listHonoursPageSizeAndSortParameters() throws Exception {
        mockMvc.perform(get("/api/documents").param("page", "2").param("size", "5").param("sort", "filename,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalPages").value(9));

        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "filename"));
    }

    @Test
    void listCapsPageSizeAt100() throws Exception {
        mockMvc.perform(get("/api/documents").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listRejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/documents").param("sort", "ocrText"))
                .andExpect(status().isBadRequest());

        verify(documentRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void unexpectedRestFailureReturnsGenericServerError() throws Exception {
        when(documentRepository.findAll(any(Pageable.class)))
                .thenThrow(new IllegalStateException("database details"));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.path").value("/api/documents"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void deleteRemovesLabelLinksBeforeDocument() throws Exception {
        when(documentRepository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/documents/1"))
                .andExpect(status().isNoContent());

        // links first, otherwise the foreign key from document_labels blocks the delete
        InOrder order = inOrder(documentLabelRepository, documentRepository);
        order.verify(documentLabelRepository).deleteByDocumentId(1L);
        order.verify(documentRepository).deleteById(1L);
    }

    @Test
    void deleteMissingDocumentReturns404() throws Exception {
        when(documentRepository.existsById(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/documents/99"))
                .andExpect(status().isNotFound());

        verify(documentLabelRepository, never()).deleteByDocumentId(anyLong());
        verify(documentRepository, never()).deleteById(anyLong());
    }

    @Test
    void corsPreflightFromUiOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/documents")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsRequestFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(get("/api/documents").header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());
    }

        @Test
    void upload_savesDocumentAndPublishesOcrJob() throws Exception {
        Document saved = new Document();
        saved.setId(5L);
        saved.setFilename("a.pdf");
        saved.setUploadDate(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(documentRepository.save(any(Document.class))).thenReturn(saved);

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", "hello".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated());

        verify(ocrJobPublisher).publish(saved);
    }

    @Test
    void upload_emptyFile_returns400AndPublishesNothing() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/documents").file(empty))
                .andExpect(status().isBadRequest());

        verify(documentRepository, never()).save(any(Document.class));
        verify(ocrJobPublisher, never()).publish(any(Document.class));
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(documentRepository).findAll(captor.capture());
        return captor.getValue();
    }
}
