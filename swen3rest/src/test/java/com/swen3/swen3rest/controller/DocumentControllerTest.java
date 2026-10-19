package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.service.DocumentService;
import com.swen3.swen3rest.service.exception.InvalidRequestException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for DocumentController: HTTP mapping, validation, error responses, pagination and CORS.
 *
 * @WebMvcTest starts only the MVC layer (controllers, ApiExceptionHandler, CorsConfig, JSON, validation)
 * without a database; DocumentService is replaced by a Mockito mock, so business rules are not tested here
 * but in DocumentServiceImplTest.
 */
@WebMvcTest(DocumentController.class)
@ExtendWith(OutputCaptureExtension.class)
class DocumentControllerTest {

    private static final DocumentDto INVOICE =
            new DocumentDto(7L, "invoice.pdf", LocalDateTime.of(2026, 10, 1, 12, 0), null, null, null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void uploadReturns201WithLocationAndDocument() throws Exception {
        when(documentService.upload("invoice.pdf", 8)).thenReturn(INVOICE);

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF-1.7".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/documents/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.filename").value("invoice.pdf"))
                .andExpect(jsonPath("$.uploadDate").value("2026-10-01T12:00:00"));
    }

    @Test
    void uploadRejectsEmptyFileBeforeReachingTheService(CapturedOutput output) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("file must not be empty"));

        verify(documentService, never()).upload(anyString(), anyLong());
        assertThat(output).contains("WARN")
                .contains("Rejected request POST /api/documents with 400: file must not be empty");
    }

    @Test
    void uploadWithoutFilePartReturns400() throws Exception {
        mockMvc.perform(multipart("/api/documents"))
                .andExpect(status().isBadRequest());

        verify(documentService, never()).upload(anyString(), anyLong());
    }

    @Test
    void listUsesDefaultPageSizeAndNewestFirst() throws Exception {
        stubListWith41Documents();

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
        stubListWith41Documents();

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
        stubListWith41Documents();

        mockMvc.perform(get("/api/documents").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listReturns400WhenTheServiceRejectsTheSortField() throws Exception {
        when(documentService.findAll(any(Pageable.class)))
                .thenThrow(new InvalidRequestException("cannot sort by 'ocrText', allowed: [id, filename, uploadDate]"));

        mockMvc.perform(get("/api/documents").param("sort", "ocrText"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("cannot sort by 'ocrText', allowed: [id, filename, uploadDate]"));
    }

    @Test
    void getReturnsTheDocument() throws Exception {
        when(documentService.findById(7L)).thenReturn(INVOICE);

        mockMvc.perform(get("/api/documents/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("invoice.pdf"))
                .andExpect(jsonPath("$.ocrText").doesNotExist());
    }

    @Test
    void getMissingDocumentReturns404ProblemDetail() throws Exception {
        when(documentService.findById(99L)).thenThrow(new NotFoundException("document 99 not found"));

        mockMvc.perform(get("/api/documents/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("document 99 not found"))
                .andExpect(jsonPath("$.instance").value("/api/documents/99"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/documents/7"))
                .andExpect(status().isNoContent());

        verify(documentService).delete(7L);
    }

    @Test
    void deleteMissingDocumentReturns404() throws Exception {
        doThrow(new NotFoundException("document 99 not found")).when(documentService).delete(99L);

        mockMvc.perform(delete("/api/documents/99"))
                .andExpect(status().isNotFound());
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

        verify(documentService, never()).findAll(any(Pageable.class));
    }

    // pretend the DB holds 41 documents in total and return one of them for any requested page
    private void stubListWith41Documents() {
        when(documentService.findAll(any(Pageable.class)))
                .thenAnswer(inv -> PageResponse.from(new PageImpl<>(List.of(INVOICE), inv.getArgument(0), 41)));
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(documentService).findAll(captor.capture());
        return captor.getValue();
    }
}
