package com.swen3.swen3rest.controller;

import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.entity.DocumentLabel;
import com.swen3.swen3rest.entity.Label;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.repository.LabelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/labels")
public class DocumentLabelController {

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "label not found"));
        if (documentLabelRepository.findByDocumentIdAndLabelId(documentId, labelId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "label already attached to document");
        }

        DocumentLabel documentLabel = new DocumentLabel();
        documentLabel.setDocument(document);
        documentLabel.setLabel(label);
        documentLabel.setWeight(weight != null ? weight : 1.0);
        DocumentLabel saved = documentLabelRepository.save(documentLabel);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<DocumentLabel> list(@PathVariable Long documentId) {
        return documentLabelRepository.findByDocumentIdOrderByWeightDesc(documentId);
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<Void> detach(@PathVariable Long documentId, @PathVariable Long labelId) {
        DocumentLabel documentLabel = documentLabelRepository.findByDocumentIdAndLabelId(documentId, labelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        documentLabelRepository.delete(documentLabel);
        return ResponseEntity.noContent().build();
    }
}
