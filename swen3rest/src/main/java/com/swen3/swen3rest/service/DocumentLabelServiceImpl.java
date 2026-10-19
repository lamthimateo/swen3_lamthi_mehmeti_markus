package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.AttachLabelRequest;
import com.swen3.swen3rest.dto.DocumentLabelDto;
import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.entity.DocumentLabel;
import com.swen3.swen3rest.entity.Label;
import com.swen3.swen3rest.mapper.DocumentLabelMapper;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.repository.LabelRepository;
import com.swen3.swen3rest.service.exception.ConflictException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentLabelServiceImpl implements DocumentLabelService {

    private static final Logger log = LoggerFactory.getLogger(DocumentLabelServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final LabelRepository labelRepository;
    private final DocumentLabelRepository documentLabelRepository;
    private final DocumentLabelMapper documentLabelMapper;

    public DocumentLabelServiceImpl(DocumentRepository documentRepository,
                                    LabelRepository labelRepository,
                                    DocumentLabelRepository documentLabelRepository,
                                    DocumentLabelMapper documentLabelMapper) {
        this.documentRepository = documentRepository;
        this.labelRepository = labelRepository;
        this.documentLabelRepository = documentLabelRepository;
        this.documentLabelMapper = documentLabelMapper;
    }

    @Override
    @Transactional
    public DocumentLabelDto attach(Long documentId, AttachLabelRequest request) {
        Long labelId = request.labelId();
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("document " + documentId + " not found"));
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new NotFoundException("label " + labelId + " not found"));
        if (documentLabelRepository.findByDocumentIdAndLabelId(documentId, labelId).isPresent()) {
            throw alreadyAttached(documentId, labelId, null);
        }

        DocumentLabel documentLabel = new DocumentLabel();
        documentLabel.setDocument(document);
        documentLabel.setLabel(label);
        if (request.weight() != null) {
            documentLabel.setWeight(request.weight());   // otherwise the entity default of 1.0 stays
        }
        DocumentLabel saved;
        try {
            saved = documentLabelRepository.saveAndFlush(documentLabel);
        } catch (DataIntegrityViolationException e) {
            // a parallel request attached the same label first; the unique (document_id, label_id) constraint caught it
            throw alreadyAttached(documentId, labelId, e);
        }
        log.info("Attached label {} '{}' to document {} (weight {})",
                labelId, label.getName(), documentId, saved.getWeight());
        return documentLabelMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentLabelDto> findByDocument(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new NotFoundException("document " + documentId + " not found");
        }
        log.debug("Listing labels of document {}", documentId);
        return documentLabelMapper.toDtos(documentLabelRepository.findByDocumentIdOrderByWeightDesc(documentId));
    }

    @Override
    @Transactional
    public void detach(Long documentId, Long labelId) {
        DocumentLabel documentLabel = documentLabelRepository.findByDocumentIdAndLabelId(documentId, labelId)
                .orElseThrow(() -> new NotFoundException(
                        "label " + labelId + " is not attached to document " + documentId));
        documentLabelRepository.delete(documentLabel);
        log.info("Detached label {} from document {}", labelId, documentId);
    }

    private static ConflictException alreadyAttached(Long documentId, Long labelId, Throwable cause) {
        return new ConflictException("label " + labelId + " is already attached to document " + documentId, cause);
    }
}
