package com.swen3.swen3rest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Core entity representing an uploaded document in the system.
 * Maps to the "documents" table in PostgreSQL.
 *
 * Fields are populated incrementally across sprints:
 * - id, filename, uploadDate: set on initial upload (Sprint 1)
 * - ocrText: filled in by the OCR worker (Sprint 4)
 * - summary: filled in by the GenAI worker (Sprint 5)
 * - storagePath: set once the file is stored in MinIO (Sprint 4)
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id                                                   // marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)    // DB auto-increments the id (Postgres SERIAL/IDENTITY)
    private Long id;

    @Column(nullable = false)                // filename is required, can't be NULL in the DB
    private String filename;

    @Column(nullable = false)                // upload timestamp is required
    private LocalDateTime uploadDate;

    @Column(columnDefinition = "TEXT")       // TEXT instead of default VARCHAR(255) — OCR output can be long
    private String ocrText;                  // raw text extracted by the OCR worker; null until Sprint 4 runs

    @Column(columnDefinition = "TEXT")       // TEXT for the same reason — summaries can exceed 255 chars
    private String summary;                  // AI-generated summary; null until Sprint 5 runs

    private String storagePath;              // MinIO object key/path; null until the file is actually stored
}