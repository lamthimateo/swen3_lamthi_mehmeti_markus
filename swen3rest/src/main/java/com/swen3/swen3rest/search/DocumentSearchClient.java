package com.swen3.swen3rest.search;

import java.util.List;

/** Full-text search over the OCR results the worker has indexed. */
public interface DocumentSearchClient {

    /**
     * @return ids of the matching documents, best match first; empty if nothing matches or nothing is indexed yet
     * @throws SearchUnavailableException if the search index could not be reached
     */
    List<Long> findDocumentIds(String query, int limit);
}
