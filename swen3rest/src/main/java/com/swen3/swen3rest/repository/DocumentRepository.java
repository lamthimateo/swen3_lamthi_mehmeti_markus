package com.swen3.swen3rest.repository;

import com.swen3.swen3rest.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Document entities, following the repository pattern
 * required by the assignment.
 *
 * Extending JpaRepository gives us CRUD operations (save, findById,
 * findAll, deleteById, etc.) for free — Spring Data JPA generates the
 * implementation at runtime, so there's no need to write SQL or DAO
 * boilerplate by hand.
 *
 * Generic parameters: <Document, Long>
 * - Document: the entity type this repository manages
 * - Long: the type of Document's primary key (id)
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    // No custom methods needed yet — Sprint 1 only requires basic CRUD.
    // Custom query methods (e.g. findByFilename) can be added here later
    // simply by declaring a method signature; Spring Data derives the
    // query from the method name.
}