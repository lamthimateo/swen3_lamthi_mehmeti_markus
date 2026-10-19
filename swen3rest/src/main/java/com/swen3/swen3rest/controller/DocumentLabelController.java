package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.dto.AttachLabelRequest;
import com.swen3.swen3rest.dto.DocumentLabelDto;
import com.swen3.swen3rest.service.DocumentLabelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/labels")
public class DocumentLabelController {

    private final DocumentLabelService documentLabelService;

    public DocumentLabelController(DocumentLabelService documentLabelService) {
        this.documentLabelService = documentLabelService;
    }

    // binds ?labelId=...&weight=... into the request record
    @PostMapping
    public ResponseEntity<DocumentLabelDto> attach(@PathVariable Long documentId,
                                                   @Valid @ModelAttribute AttachLabelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentLabelService.attach(documentId, request));
    }

    @GetMapping
    public List<DocumentLabelDto> list(@PathVariable Long documentId) {
        return documentLabelService.findByDocument(documentId);
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<Void> detach(@PathVariable Long documentId, @PathVariable Long labelId) {
        documentLabelService.detach(documentId, labelId);
        return ResponseEntity.noContent().build();
    }
}
