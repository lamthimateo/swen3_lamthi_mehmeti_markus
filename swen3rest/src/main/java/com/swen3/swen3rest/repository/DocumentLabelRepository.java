package com.swen3.swen3rest.repository;

import com.swen3.swen3rest.entity.DocumentLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentLabelRepository extends JpaRepository<DocumentLabel, Long> {
    List<DocumentLabel> findByDocumentIdOrderByWeightDesc(Long documentId);
    Optional<DocumentLabel> findByDocumentIdAndLabelId(Long documentId, Long labelId);

    // removes all label links of a document; must run inside a transaction
    void deleteByDocumentId(Long documentId);
}
