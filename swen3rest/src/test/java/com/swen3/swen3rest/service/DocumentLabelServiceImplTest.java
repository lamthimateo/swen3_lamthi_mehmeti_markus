package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.AttachLabelRequest;
import com.swen3.swen3rest.dto.DocumentLabelDto;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.entity.Document;
import com.swen3.swen3rest.entity.DocumentLabel;
import com.swen3.swen3rest.entity.Label;
import com.swen3.swen3rest.mapper.DocumentLabelMapperImpl;
import com.swen3.swen3rest.mapper.LabelMapperImpl;
import com.swen3.swen3rest.repository.DocumentLabelRepository;
import com.swen3.swen3rest.repository.DocumentRepository;
import com.swen3.swen3rest.repository.LabelRepository;
import com.swen3.swen3rest.service.exception.ConflictException;
import com.swen3.swen3rest.service.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the labelling use case, with repositories mocked and the real generated mappers.
 */
@ExtendWith(MockitoExtension.class)
class DocumentLabelServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private DocumentLabelRepository documentLabelRepository;

    private DocumentLabelServiceImpl documentLabelService;

    private final Document invoice = document(1L, "invoice.pdf");
    private final Label urgent = new Label(2L, "urgent");

    @BeforeEach
    void setUp() {
        documentLabelService = new DocumentLabelServiceImpl(documentRepository, labelRepository,
                documentLabelRepository, new DocumentLabelMapperImpl(new LabelMapperImpl()));
    }

    @Test
    void attachLinksTheLabelWithTheGivenWeight() {
        stubDocumentAndLabelExistButAreNotLinked();
        when(documentLabelRepository.saveAndFlush(any(DocumentLabel.class))).thenAnswer(inv -> {
            DocumentLabel saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        DocumentLabelDto link = documentLabelService.attach(1L, new AttachLabelRequest(2L, 0.5));

        assertThat(link).isEqualTo(new DocumentLabelDto(10L, 1L, new LabelDto(2L, "urgent"), 0.5));
    }

    @Test
    void attachWithoutWeightUsesTheDefaultOfOne() {
        stubDocumentAndLabelExistButAreNotLinked();
        when(documentLabelRepository.saveAndFlush(any(DocumentLabel.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentLabelDto link = documentLabelService.attach(1L, new AttachLabelRequest(2L, null));

        assertThat(link.weight()).isEqualTo(1.0);
    }

    @Test
    void attachToMissingDocumentThrowsNotFound() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentLabelService.attach(99L, new AttachLabelRequest(2L, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("document 99 not found");
        verifyNoInteractions(labelRepository, documentLabelRepository);
    }

    @Test
    void attachMissingLabelThrowsNotFound() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(labelRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentLabelService.attach(1L, new AttachLabelRequest(42L, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("label 42 not found");
        verifyNoInteractions(documentLabelRepository);
    }

    @Test
    void attachAlreadyAttachedLabelThrowsConflict() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(labelRepository.findById(2L)).thenReturn(Optional.of(urgent));
        when(documentLabelRepository.findByDocumentIdAndLabelId(1L, 2L))
                .thenReturn(Optional.of(new DocumentLabel(10L, invoice, urgent, 1.0)));

        assertThatThrownBy(() -> documentLabelService.attach(1L, new AttachLabelRequest(2L, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("label 2 is already attached to document 1");
        verify(documentLabelRepository, never()).saveAndFlush(any(DocumentLabel.class));
    }

    @Test
    void attachTurnsAUniqueConstraintViolationIntoAConflict() {
        stubDocumentAndLabelExistButAreNotLinked();
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException("unique constraint");
        when(documentLabelRepository.saveAndFlush(any(DocumentLabel.class))).thenThrow(duplicate);

        assertThatThrownBy(() -> documentLabelService.attach(1L, new AttachLabelRequest(2L, null)))
                .isInstanceOf(ConflictException.class)
                .hasCause(duplicate);
    }

    @Test
    void findByDocumentReturnsMappedLinks() {
        Label misc = new Label(3L, "misc");
        when(documentRepository.existsById(1L)).thenReturn(true);
        when(documentLabelRepository.findByDocumentIdOrderByWeightDesc(1L)).thenReturn(List.of(
                new DocumentLabel(10L, invoice, urgent, 0.9),
                new DocumentLabel(11L, invoice, misc, 0.2)));

        List<DocumentLabelDto> links = documentLabelService.findByDocument(1L);

        assertThat(links).containsExactly(
                new DocumentLabelDto(10L, 1L, new LabelDto(2L, "urgent"), 0.9),
                new DocumentLabelDto(11L, 1L, new LabelDto(3L, "misc"), 0.2));
    }

    @Test
    void findByMissingDocumentThrowsNotFound() {
        when(documentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> documentLabelService.findByDocument(99L)).isInstanceOf(NotFoundException.class);
        verify(documentLabelRepository, never()).findByDocumentIdOrderByWeightDesc(anyLong());
    }

    @Test
    void detachDeletesTheLink() {
        DocumentLabel link = new DocumentLabel(10L, invoice, urgent, 1.0);
        when(documentLabelRepository.findByDocumentIdAndLabelId(1L, 2L)).thenReturn(Optional.of(link));

        documentLabelService.detach(1L, 2L);

        verify(documentLabelRepository).delete(link);
    }

    @Test
    void detachLabelThatIsNotAttachedThrowsNotFound() {
        when(documentLabelRepository.findByDocumentIdAndLabelId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentLabelService.detach(1L, 2L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("label 2 is not attached to document 1");
        verify(documentLabelRepository, never()).delete(any(DocumentLabel.class));
    }

    private void stubDocumentAndLabelExistButAreNotLinked() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(labelRepository.findById(2L)).thenReturn(Optional.of(urgent));
        when(documentLabelRepository.findByDocumentIdAndLabelId(1L, 2L)).thenReturn(Optional.empty());
    }

    private static Document document(Long id, String filename) {
        Document document = new Document();
        document.setId(id);
        document.setFilename(filename);
        document.setUploadDate(LocalDateTime.of(2026, 10, 1, 12, 0));
        return document;
    }
}
