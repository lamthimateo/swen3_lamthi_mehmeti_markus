package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.dto.CreateLabelRequest;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.service.LabelService;
import com.swen3.swen3rest.service.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for LabelController: Bean Validation of CreateLabelRequest and error mapping.
 */
@WebMvcTest(LabelController.class)
class LabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LabelService labelService;

    @Test
    void createTrimsTheNameAndReturns201() throws Exception {
        when(labelService.create(new CreateLabelRequest("invoice"))).thenReturn(new LabelDto(3L, "invoice"));

        mockMvc.perform(post("/api/labels").param("name", "  invoice "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("invoice"));

        verify(labelService).create(new CreateLabelRequest("invoice"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/labels").param("name", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("name must not be blank"));

        verify(labelService, never()).create(any(CreateLabelRequest.class));
    }

    @Test
    void createRejectsMissingName() throws Exception {
        mockMvc.perform(post("/api/labels"))
                .andExpect(status().isBadRequest());

        verify(labelService, never()).create(any(CreateLabelRequest.class));
    }

    @Test
    void createRejectsNameLongerThan50Characters() throws Exception {
        mockMvc.perform(post("/api/labels").param("name", "x".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("name size must be between 0 and 50"));

        verify(labelService, never()).create(any(CreateLabelRequest.class));
    }

    @Test
    void createDuplicateNameReturns409() throws Exception {
        when(labelService.create(new CreateLabelRequest("invoice")))
                .thenThrow(new ConflictException("label 'invoice' already exists"));

        mockMvc.perform(post("/api/labels").param("name", "invoice"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("label 'invoice' already exists"));
    }

    @Test
    void listReturnsAllLabels() throws Exception {
        when(labelService.findAll()).thenReturn(List.of(new LabelDto(1L, "archive"), new LabelDto(3L, "invoice")));

        mockMvc.perform(get("/api/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("archive"))
                .andExpect(jsonPath("$[1].name").value("invoice"));
    }
}
