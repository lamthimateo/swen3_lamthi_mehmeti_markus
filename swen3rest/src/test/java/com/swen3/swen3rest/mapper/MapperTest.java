package com.swen3.swen3rest.mapper;

import com.swen3.swen3rest.dto.CreateLabelRequest;
import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.dto.DocumentLabelDto;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.entity.DocumentLabel;
import com.swen3.swen3rest.entity.Label;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the mapper implementations MapStruct generates at compile time (target/generated-sources).
 * No Spring context needed: the generated classes are plain Java.
 */
class MapperTest {

    private final DocumentMapper documentMapper = new DocumentMapperImpl();
    private final LabelMapper labelMapper = new LabelMapperImpl();
    private final DocumentLabelMapper documentLabelMapper = new DocumentLabelMapperImpl(labelMapper);

    @Test
    void documentMapperCopiesEveryField() {
        LocalDateTime uploaded = LocalDateTime.of(2026, 10, 1, 12, 0);
        Document document = new Document(1L, "invoice.pdf", uploaded, "Total: 42 EUR", "An invoice", "docs/1.pdf");

        assertThat(documentMapper.toDto(document)).isEqualTo(
                new DocumentDto(1L, "invoice.pdf", uploaded, "Total: 42 EUR", "An invoice", "docs/1.pdf"));
    }

    @Test
    void labelMapperLeavesTheIdToTheDatabase() {
        Label label = labelMapper.toEntity(new CreateLabelRequest("invoice"));

        assertThat(label.getId()).isNull();
        assertThat(label.getName()).isEqualTo("invoice");
    }

    @Test
    void documentLabelMapperFlattensTheDocumentAndMapsTheNestedLabel() {
        Document document = new Document();
        document.setId(1L);
        DocumentLabel link = new DocumentLabel(10L, document, new Label(2L, "urgent"), 0.9);

        assertThat(documentLabelMapper.toDto(link))
                .isEqualTo(new DocumentLabelDto(10L, 1L, new LabelDto(2L, "urgent"), 0.9));
    }
}
