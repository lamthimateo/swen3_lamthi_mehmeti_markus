package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.service.exception.InvalidRequestException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import org.springframework.data.domain.Pageable;

import java.io.InputStream;
import java.util.List;

/**
 * Business logic for documents. Controllers depend on this interface, not on the
 * implementation, so the web layer can be tested with a mock and the implementation
 * can change (e.g. MinIO storage in Sprint 4) without touching the controllers.
 */
public interface DocumentService {

    /**
     * Stores the file in MinIO and the document's metadata in the database, then queues the document for OCR.
     * Either all of it happens or none: if storing or queueing fails, no document row is left behind.
     *
     * @param size exact number of bytes in {@code content}
     */
    DocumentDto upload(String filename, long size, String contentType, InputStream content);

    /**
     * Full-text search over the OCR results, best match first.
     *
     * @throws InvalidRequestException if the search term is blank
     */
    List<DocumentDto> search(String query);

    /** @throws InvalidRequestException if the page is sorted by a field that is not sortable */
    PageResponse<DocumentDto> findAll(Pageable pageable);

    /** @throws NotFoundException if there is no document with this id */
    DocumentDto findById(Long id);

    /** Deletes the document with its label links and its stored file. @throws NotFoundException if it does not exist */
    void delete(Long id);
}
