package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.dto.CreateLabelRequest;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.service.LabelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/labels")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    // @ModelAttribute binds the query parameter ?name=... into the request record; @Valid checks its constraints
    @PostMapping
    public ResponseEntity<LabelDto> create(@Valid @ModelAttribute CreateLabelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labelService.create(request));
    }

    @GetMapping
    public List<LabelDto> list() {
        return labelService.findAll();
    }
}
