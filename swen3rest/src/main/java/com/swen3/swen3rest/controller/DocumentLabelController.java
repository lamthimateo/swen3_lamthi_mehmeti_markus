package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.entity.DocumentLabel;
import com.swen3.swen3rest.entity.Label;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.repository.LabelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/labels")
public class DocumentLabelController {

    private static final Logger log = LoggerFactory.getLogger(DocumentLabelController.class);

    private final DocumentRepository documentRepository;
    private final LabelRepository labelRepository;
    private final DocumentLabelRepository documentLabelRepository;

    public DocumentLabelController(DocumentRepository documentRepository,
                                    LabelRepository labelRepository,
                                    DocumentLabelRepository documentLabelRepository) {
        this.documentRepository = documentRepository;
        this.labelRepository = labelRepository;
        this.documentLabelRepository = documentLabelRepository;
    }

    @PostMapping
    public ResponseEntity<DocumentLabel> attach(@PathVariable Long documentId,
                                                 @RequestParam Long labelId,
                                                 @RequestParam(required = false) Double weight) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.warn("Cannot attach label {}: document {} not found", labelId, documentId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
                });
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> {
                    log.warn("Cannot attach label {} to document {}: label not found", labelId, documentId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "label not found");
                });
        if (documentLabelRepository.findByDocumentIdAndLabelId(documentId, labelId).isPresent()) {
            log.warn("Rejected attaching label {} to document {}: already attached", labelId, documentId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "label already attached to document");
        }

        DocumentLabel documentLabel = new DocumentLabel();
        documentLabel.setDocument(document);
        documentLabel.setLabel(label);
        documentLabel.setWeight(weight != null ? weight : 1.0);
        DocumentLabel saved = documentLabelRepository.save(documentLabel);
        log.info("Attached label {} '{}' to document {} (weight {})",
                labelId, label.getName(), documentId, saved.getWeight());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<DocumentLabel> list(@PathVariable Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            log.warn("Cannot list labels: document {} not found", documentId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        log.debug("Listing labels of document {}", documentId);
        return documentLabelRepository.findByDocumentIdOrderByWeightDesc(documentId);
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<Void> detach(@PathVariable Long documentId, @PathVariable Long labelId) {
        DocumentLabel documentLabel = documentLabelRepository.findByDocumentIdAndLabelId(documentId, labelId)
                .orElseThrow(() -> {
                    log.warn("Cannot detach label {}: not attached to document {}", labelId, documentId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND);
                });
        documentLabelRepository.delete(documentLabel);
        log.info("Detached label {} from document {}", labelId, documentId);
        return ResponseEntity.noContent().build();
    }
}
