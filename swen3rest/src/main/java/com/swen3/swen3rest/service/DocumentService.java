package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.PageResponse;
import com.swen3.swen3rest.service.exception.InvalidRequestException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import org.springframework.data.domain.Pageable;

/**
 * Business logic for documents. Controllers depend on this interface, not on the
 * implementation, so the web layer can be tested with a mock and the implementation
 * can change (e.g. MinIO storage in Sprint 4) without touching the controllers.
 */
public interface DocumentService {

    /** Stores the document's metadata and queues it for OCR. */
    DocumentDto upload(String filename, long size);

    /** @throws InvalidRequestException if the page is sorted by a field that is not sortable */
    PageResponse<DocumentDto> findAll(Pageable pageable);

    /** @throws NotFoundException if there is no document with this id */
    DocumentDto findById(Long id);

    /** Deletes the document together with its label links. @throws NotFoundException if it does not exist */
    void delete(Long id);
}
