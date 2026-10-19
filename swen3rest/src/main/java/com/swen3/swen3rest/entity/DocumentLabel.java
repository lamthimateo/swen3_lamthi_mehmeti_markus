package com.swen3.swen3rest.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "document_labels", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "label_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentLabel {
    public static final double DEFAULT_WEIGHT = 1.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(optional = false)
    @JoinColumn(name = "label_id", nullable = false)
    private Label label;

    // relevance/confidence of this label for the document; lets us rank/prioritize labels per document,
    // and doubles as a slot for a future GenAI auto-tagging confidence score
    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Double weight;
}
