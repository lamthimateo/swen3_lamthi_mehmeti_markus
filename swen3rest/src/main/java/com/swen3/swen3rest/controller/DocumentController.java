package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.repository.DocumentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "filename", "uploadDate");

    private final DocumentRepository documentRepository;

    public DocumentController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @PostMapping
    public ResponseEntity<Document> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must not be empty");
        }
        Document document = new Document();
        document.setFilename(file.getOriginalFilename());
        document.setUploadDate(LocalDateTime.now());
        Document saved = documentRepository.save(document);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public PageResponse<Document> list(
            @PageableDefault(size = 20, sort = "uploadDate", direction = Sort.Direction.DESC) Pageable pageable) {
        // reject unknown sort fields with 400 instead of letting the query fail with 500
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cannot sort by '" + order.getProperty() + "', allowed: " + SORTABLE_FIELDS);
            }
        }
        return PageResponse.from(documentRepository.findAll(pageable));
    }

    @GetMapping("/{id}")
    public Document get(@PathVariable Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!documentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        documentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
