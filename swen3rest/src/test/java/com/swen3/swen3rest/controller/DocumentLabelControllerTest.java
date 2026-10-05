package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.entity.DocumentLabel;
import com.swen3.swen3rest.entity.Label;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.repository.LabelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for DocumentLabelController, with all repositories mocked.
 */
@WebMvcTest(DocumentLabelController.class)
class DocumentLabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentRepository documentRepository;

    @MockitoBean
    private LabelRepository labelRepository;

    @MockitoBean
    private DocumentLabelRepository documentLabelRepository;

    @Test
    void listForMissingDocumentReturns404() throws Exception {
        when(documentRepository.existsById(99L)).thenReturn(false);

        mockMvc.perform(get("/api/documents/99/labels"))
                .andExpect(status().isNotFound());

        verify(documentLabelRepository, never()).findByDocumentIdOrderByWeightDesc(anyLong());
    }

    @Test
    void listForExistingDocumentReturnsItsLabels() throws Exception {
        Document document = new Document();
        document.setId(1L);
        document.setFilename("invoice.pdf");
        document.setUploadDate(LocalDateTime.of(2026, 10, 1, 12, 0));

        Label label = new Label(1L, "urgent");
        DocumentLabel link = new DocumentLabel(1L, document, label, 0.9);

        when(documentRepository.existsById(1L)).thenReturn(true);
        when(documentLabelRepository.findByDocumentIdOrderByWeightDesc(1L)).thenReturn(List.of(link));

        mockMvc.perform(get("/api/documents/1/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label.name").value("urgent"))
                .andExpect(jsonPath("$[0].weight").value(0.9));
    }
}
