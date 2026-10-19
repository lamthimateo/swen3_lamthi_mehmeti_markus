package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.CreateLabelRequest;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.entity.Label;
import com.swen3.swen3rest.mapper.LabelMapperImpl;
import com.swen3.swen3rest.repository.LabelRepository;
import com.swen3.swen3rest.service.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabelServiceImplTest {

    @Mock
    private LabelRepository labelRepository;

    private LabelServiceImpl labelService;

    @BeforeEach
    void setUp() {
        labelService = new LabelServiceImpl(labelRepository, new LabelMapperImpl());
    }

    @Test
    void createSavesTheLabelAndReturnsIt() {
        when(labelRepository.existsByNameIgnoreCase("invoice")).thenReturn(false);
        when(labelRepository.saveAndFlush(any(Label.class))).thenAnswer(inv -> {
            Label saved = inv.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        LabelDto created = labelService.create(new CreateLabelRequest("invoice"));

        assertThat(created).isEqualTo(new LabelDto(3L, "invoice"));
        ArgumentCaptor<Label> saved = ArgumentCaptor.forClass(Label.class);
        verify(labelRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("invoice");
    }

    @Test
    void createRejectsNameThatExistsInAnotherCase() {
        when(labelRepository.existsByNameIgnoreCase("Invoice")).thenReturn(true);

        assertThatThrownBy(() -> labelService.create(new CreateLabelRequest("Invoice")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("label 'Invoice' already exists");
        verify(labelRepository, never()).saveAndFlush(any(Label.class));
    }

    @Test
    void createTurnsAUniqueConstraintViolationIntoAConflict() {
        // simulates a parallel request inserting the same name between the check and the insert
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException("unique constraint");
        when(labelRepository.existsByNameIgnoreCase("invoice")).thenReturn(false);
        when(labelRepository.saveAndFlush(any(Label.class))).thenThrow(duplicate);

        assertThatThrownBy(() -> labelService.create(new CreateLabelRequest("invoice")))
                .isInstanceOf(ConflictException.class)
                .hasCause(duplicate);
    }

    @Test
    void findAllReturnsLabelsSortedByName() {
        when(labelRepository.findAll(Sort.by("name")))
                .thenReturn(List.of(new Label(1L, "archive"), new Label(3L, "invoice")));

        assertThat(labelService.findAll())
                .containsExactly(new LabelDto(1L, "archive"), new LabelDto(3L, "invoice"));
    }
}
