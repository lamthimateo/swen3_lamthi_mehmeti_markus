package com.swen3.swen3rest.repository;

import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.entity.DocumentLabel;
import com.swen3.swen3rest.entity.Label;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DocumentLabelRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private DocumentLabelRepository documentLabelRepository;

    @Test
    void ordersLabelsByWeightDescending() {
        Document document = new Document();
        document.setFilename("invoice.pdf");
        document.setUploadDate(LocalDateTime.now());
        document = documentRepository.save(document);

        Label urgent = new Label();
        urgent.setName("urgent");
        urgent = labelRepository.save(urgent);

        Label misc = new Label();
        misc.setName("misc");
        misc = labelRepository.save(misc);

        attach(document, misc, 0.2);
        attach(document, urgent, 0.9);

        List<DocumentLabel> labels = documentLabelRepository.findByDocumentIdOrderByWeightDesc(document.getId());

        assertThat(labels).hasSize(2);
        assertThat(labels.get(0).getLabel().getName()).isEqualTo("urgent");
        assertThat(labels.get(1).getLabel().getName()).isEqualTo("misc");
    }

    @Test
    void findsAssociationByDocumentAndLabel() {
        Document document = new Document();
        document.setFilename("contract.pdf");
        document.setUploadDate(LocalDateTime.now());
        document = documentRepository.save(document);

        Label label = new Label();
        label.setName("legal");
        label = labelRepository.save(label);

        attach(document, label, 1.0);

        assertThat(documentLabelRepository.findByDocumentIdAndLabelId(document.getId(), label.getId())).isPresent();
    }

    private void attach(Document document, Label label, double weight) {
        DocumentLabel documentLabel = new DocumentLabel();
        documentLabel.setDocument(document);
        documentLabel.setLabel(label);
        documentLabel.setWeight(weight);
        documentLabelRepository.save(documentLabel);
    }
}
