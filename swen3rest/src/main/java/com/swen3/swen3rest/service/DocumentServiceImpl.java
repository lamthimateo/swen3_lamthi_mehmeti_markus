package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.mapper.DocumentMapper;
import com.swen3.swen3rest.messaging.OcrJobPublisher;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.service.exception.InvalidRequestException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    // sorting by any other field (e.g. ocrText) is rejected with 400 instead of failing in the query
    private static final List<String> SORTABLE_FIELDS = List.of("id", "filename", "uploadDate");

    private final DocumentRepository documentRepository;
    private final DocumentLabelRepository documentLabelRepository;
    private final OcrJobPublisher ocrJobPublisher;
    private final DocumentMapper documentMapper;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentLabelRepository documentLabelRepository,
                               OcrJobPublisher ocrJobPublisher,
                               DocumentMapper documentMapper) {
        this.documentRepository = documentRepository;
        this.documentLabelRepository = documentLabelRepository;
        this.ocrJobPublisher = ocrJobPublisher;
        this.documentMapper = documentMapper;
    }

    @Override
    @Transactional  // if the OCR job cannot be published, the insert is rolled back: no document without a job
    public DocumentDto upload(String filename, long size) {
        Document document = new Document();
        document.setFilename(filename);
        document.setUploadDate(LocalDateTime.now());
        Document saved = documentRepository.save(document);

        ocrJobPublisher.publish(saved);
        log.info("Uploaded document {} ('{}', {} bytes)", saved.getId(), saved.getFilename(), size);
        return documentMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocumentDto> findAll(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "cannot sort by '" + order.getProperty() + "', allowed: " + SORTABLE_FIELDS);
            }
        }
        log.debug("Listing documents: page {}, size {}, sort {}",
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        return PageResponse.from(documentRepository.findAll(pageable).map(documentMapper::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDto findById(Long id) {
        log.debug("Fetching document {}", id);
        return documentRepository.findById(id)
                .map(documentMapper::toDto)
                .orElseThrow(() -> documentNotFound(id));
    }

    @Override
    @Transactional  // label links and document are removed together, or not at all
    public void delete(Long id) {
        if (!documentRepository.existsById(id)) {
            throw documentNotFound(id);
        }
        // document_labels has a foreign key to documents, so the links must go first
        documentLabelRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
        log.info("Deleted document {} and its label links", id);
    }

    private static NotFoundException documentNotFound(Long id) {
        return new NotFoundException("document " + id + " not found");
    }
}
