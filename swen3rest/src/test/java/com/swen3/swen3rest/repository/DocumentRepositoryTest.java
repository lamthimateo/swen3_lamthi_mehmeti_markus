package com.swen3.swen3rest.repository;

import com.swen3.swen3rest.entity.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DocumentRepository.
 *
 * @DataJpaTest spins up an in-memory H2 database just for this test
 * class instead of connecting to the real PostgreSQL container — this
 * satisfies the "mock out the production database" requirement. Each
 * test also runs inside a transaction that's rolled back afterward, so
 * tests never leave leftover data behind or affect each other.
 */
@DataJpaTest
class DocumentRepositoryTest {

    @Autowired                                   // Spring injects a repository wired to the H2 test DB
    private DocumentRepository documentRepository;

    @Test
    void savesAndRetrievesDocument() {
        // Arrange: build a Document the way it would look right after upload
        Document document = new Document();
        document.setFilename("test.pdf");
        document.setUploadDate(LocalDateTime.now());

        // Act: persist it
        Document saved = documentRepository.save(document);

        // Assert: the DB generated an id, proving the insert happened
        assertThat(saved.getId()).isNotNull();

        // Act: fetch it back by id
        Optional<Document> found = documentRepository.findById(saved.getId());

        // Assert: it round-tripped correctly
        assertThat(found).isPresent();
        assertThat(found.get().getFilename()).isEqualTo("test.pdf");
    }

    @Test
    void deletesDocument() {
        // Arrange: create and save a document to delete
        Document document = new Document();
        document.setFilename("delete-me.pdf");
        document.setUploadDate(LocalDateTime.now());
        Document saved = documentRepository.save(document);

        // Act: delete it
        documentRepository.deleteById(saved.getId());

        // Assert: it's really gone
        assertThat(documentRepository.findById(saved.getId())).isEmpty();
    }
}