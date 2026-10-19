package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.AttachLabelRequest;
import com.swen3.swen3rest.dto.DocumentLabelDto;
import com.swen3.swen3rest.service.exception.ConflictException;
import com.swen3.swen3rest.service.exception.NotFoundException;

import java.util.List;

/**
 * Business logic for the labelling use case: attaching labels to documents with a weight.
 */
public interface DocumentLabelService {

    /**
     * @throws NotFoundException if the document or the label does not exist
     * @throws ConflictException if the label is already attached to the document
     */
    DocumentLabelDto attach(Long documentId, AttachLabelRequest request);

    /** Labels of the document, highest weight first. @throws NotFoundException if the document does not exist */
    List<DocumentLabelDto> findByDocument(Long documentId);

    /** @throws NotFoundException if the label is not attached to the document */
    void detach(Long documentId, Long labelId);
}
