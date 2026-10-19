package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.dto.AttachLabelRequest;
import com.swen3.swen3rest.dto.DocumentLabelDto;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.service.DocumentLabelService;
import com.swen3.swen3rest.service.exception.ConflictException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for DocumentLabelController, with DocumentLabelService mocked.
 */
@WebMvcTest(DocumentLabelController.class)
class DocumentLabelControllerTest {

    private static final DocumentLabelDto URGENT =
            new DocumentLabelDto(10L, 1L, new LabelDto(2L, "urgent"), 0.5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentLabelService documentLabelService;

    @Test
    void attachReturns201WithTheLink() throws Exception {
        when(documentLabelService.attach(1L, new AttachLabelRequest(2L, 0.5))).thenReturn(URGENT);

        mockMvc.perform(post("/api/documents/1/labels").param("labelId", "2").param("weight", "0.5"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(1))
                .andExpect(jsonPath("$.label.id").value(2))
                .andExpect(jsonPath("$.label.name").value("urgent"))
                .andExpect(jsonPath("$.weight").value(0.5));
    }

    @Test
    void attachWithoutWeightLeavesTheDefaultToTheService() throws Exception {
        when(documentLabelService.attach(1L, new AttachLabelRequest(2L, null))).thenReturn(URGENT);

        mockMvc.perform(post("/api/documents/1/labels").param("labelId", "2"))
                .andExpect(status().isCreated());

        verify(documentLabelService).attach(1L, new AttachLabelRequest(2L, null));
    }

    @Test
    void attachRejectsMissingLabelId() throws Exception {
        mockMvc.perform(post("/api/documents/1/labels"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("labelId must not be null"));

        verify(documentLabelService, never()).attach(anyLong(), any(AttachLabelRequest.class));
    }

    @Test
    void attachRejectsNegativeWeight() throws Exception {
        mockMvc.perform(post("/api/documents/1/labels").param("labelId", "2").param("weight", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("weight must be greater than or equal to 0"));

        verify(documentLabelService, never()).attach(anyLong(), any(AttachLabelRequest.class));
    }

    @Test
    void attachRejectsNonNumericLabelId() throws Exception {
        mockMvc.perform(post("/api/documents/1/labels").param("labelId", "abc"))
                .andExpect(status().isBadRequest());

        verify(documentLabelService, never()).attach(anyLong(), any(AttachLabelRequest.class));
    }

    @Test
    void attachAlreadyAttachedLabelReturns409() throws Exception {
        when(documentLabelService.attach(1L, new AttachLabelRequest(2L, null)))
                .thenThrow(new ConflictException("label 2 is already attached to document 1"));

        mockMvc.perform(post("/api/documents/1/labels").param("labelId", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("label 2 is already attached to document 1"));
    }

    @Test
    void listForMissingDocumentReturns404() throws Exception {
        when(documentLabelService.findByDocument(99L)).thenThrow(new NotFoundException("document 99 not found"));

        mockMvc.perform(get("/api/documents/99/labels"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listForExistingDocumentReturnsItsLabels() throws Exception {
        when(documentLabelService.findByDocument(1L)).thenReturn(List.of(URGENT));

        mockMvc.perform(get("/api/documents/1/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label.name").value("urgent"))
                .andExpect(jsonPath("$[0].weight").value(0.5));
    }

    @Test
    void detachReturns204() throws Exception {
        mockMvc.perform(delete("/api/documents/1/labels/2"))
                .andExpect(status().isNoContent());

        verify(documentLabelService).detach(1L, 2L);
    }

    @Test
    void detachLabelThatIsNotAttachedReturns404() throws Exception {
        doThrow(new NotFoundException("label 2 is not attached to document 1"))
                .when(documentLabelService).detach(1L, 2L);

        mockMvc.perform(delete("/api/documents/1/labels/2"))
                .andExpect(status().isNotFound());
    }
}
