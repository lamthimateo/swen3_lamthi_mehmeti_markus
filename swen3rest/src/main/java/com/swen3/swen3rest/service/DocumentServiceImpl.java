package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.mapper.DocumentMapper;
import com.swen3.swen3rest.messaging.OcrJobPublisher;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.service.exception.InvalidRequestException;
import com.swen3.swen3rest.search.DocumentSearchClient;
import com.swen3.swen3rest.service.exception.NotFoundException;
import com.swen3.swen3rest.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    // sorting by any other field (e.g. ocrText) is rejected with 400 instead of failing in the query
    private static final List<String> SORTABLE_FIELDS = List.of("id", "filename", "uploadDate");

    private static final int MAX_SEARCH_RESULTS = 50;

    private final DocumentRepository documentRepository;
    private final DocumentLabelRepository documentLabelRepository;
    private final OcrJobPublisher ocrJobPublisher;
    private final DocumentMapper documentMapper;
    private final FileStorage fileStorage;
    private final DocumentSearchClient searchClient;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               DocumentLabelRepository documentLabelRepository,
                               OcrJobPublisher ocrJobPublisher,
                               DocumentMapper documentMapper,
                               FileStorage fileStorage,
                               DocumentSearchClient searchClient) {
        this.documentRepository = documentRepository;
        this.documentLabelRepository = documentLabelRepository;
        this.ocrJobPublisher = ocrJobPublisher;
        this.documentMapper = documentMapper;
        this.fileStorage = fileStorage;
        this.searchClient = searchClient;
    }

    @Override
    @Transactional  // if storing the file or queueing the OCR job fails, the insert is rolled back: no document without a file and a job
    public DocumentDto upload(String filename, long size, String contentType, InputStream content) {
        Document document = new Document();
        document.setFilename(filename);
        document.setUploadDate(LocalDateTime.now());
        Document saved = documentRepository.save(document);   // the insert gives us the id used in the storage key

        // The OCR worker rebuilds this key from the job message (<documentId>/<filename>), so keep both in sync.
        String key = saved.getId() + "/" + saved.getFilename();
        fileStorage.store(key, content, size, contentType);
        saved.setStoragePath(key);   // managed entity: written to the database when the transaction commits

        try {
            ocrJobPublisher.publish(saved);
        } catch (RuntimeException e) {
            // the transaction rolls the row back, so the file in MinIO would be left without a document
            fileStorage.deleteQuietly(key);
            throw e;
        }
        log.info("Uploaded document {} ('{}', {} bytes) to '{}'", saved.getId(), saved.getFilename(), size, key);
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
    @Transactional(readOnly = true)
    public List<DocumentDto> search(String query) {
        if (query == null || query.isBlank()) {
            throw new InvalidRequestException("search term must not be empty");
        }
        List<Long> ids = searchClient.findDocumentIds(query.strip(), MAX_SEARCH_RESULTS);

        // The search index may still hold documents that were deleted since, and the database is the source of
        // truth: ids without a document are dropped, and the order of the search hits is kept.
        Map<Long, Document> existing = documentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        List<DocumentDto> results = ids.stream()
                .map(existing::get)
                .filter(Objects::nonNull)
                .map(documentMapper::toDto)
                .toList();
        log.info("Search '{}' found {} document(s)", query.strip(), results.size());
        return results;
    }

    @Override
    @Transactional  // label links and document are removed together, or not at all
    public void delete(Long id) {
        Document document = documentRepository.findById(id).orElseThrow(() -> documentNotFound(id));
        // document_labels has a foreign key to documents, so the links must go first
        documentLabelRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
        if (document.getStoragePath() != null) {
            fileStorage.deleteQuietly(document.getStoragePath());   // a leftover file is harmless, a failed delete must not undo this
        }
        log.info("Deleted document {} and its label links", id);
    }

    private static NotFoundException documentNotFound(Long id) {
        return new NotFoundException("document " + id + " not found");
    }
}
