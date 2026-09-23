package com.swen3.swen3rest.repository;

import com.swen3.swen3rest.entity.Label;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LabelRepositoryTest {

    @Autowired
    private LabelRepository labelRepository;

    @Test
    void savesAndFindsLabelByName() {
        Label label = new Label();
        label.setName("invoice");
        labelRepository.save(label);

        Optional<Label> found = labelRepository.findByName("invoice");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("invoice");
    }

    @Test
    void deletesLabel() {
        Label label = new Label();
        label.setName("contract");
        Label saved = labelRepository.save(label);

        labelRepository.deleteById(saved.getId());

        assertThat(labelRepository.findById(saved.getId())).isEmpty();
    }
}
