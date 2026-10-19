package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.service.DocumentService;
import com.swen3.swen3rest.validation.NotEmptyFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * HTTP layer for documents: translates requests into DocumentService calls and back.
 * No business rules here; errors thrown by the service are mapped to status codes by ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentDto> upload(@RequestParam("file") @NotEmptyFile MultipartFile file) {
        DocumentDto created = documentService.upload(file.getOriginalFilename(), file.getSize());
        // 201 Created + Location header pointing at the new resource
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<DocumentDto> list(
            @PageableDefault(size = 20, sort = "uploadDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return documentService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public DocumentDto get(@PathVariable Long id) {
        return documentService.findById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
