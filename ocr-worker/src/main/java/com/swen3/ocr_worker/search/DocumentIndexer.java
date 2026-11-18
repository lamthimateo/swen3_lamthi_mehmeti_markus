package com.swen3.ocr_worker.search;

import com.swen3.ocr_worker.IndexJobMessage;

/** Makes the text of a document searchable. */
public interface DocumentIndexer {

    /**
     * Stores (or replaces) the document in the search index.
     *
     * @throws IndexingException if the search index rejected the document or could not be reached
     */
    void index(IndexJobMessage message);
}
