package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for DocumentController: pagination of GET /api/documents and CORS.
 *
 * @WebMvcTest starts only the MVC layer (controller, CorsConfig, JSON, Pageable
 * resolver) without a database; the repository is replaced by a Mockito mock.
 */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentRepository documentRepository;

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
    void corsPreflightFromUiOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/documents")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
    }

    @Test
    void corsRequestFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(get("/api/documents").header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(documentRepository).findAll(captor.capture());
        return captor.getValue();
    }
}
