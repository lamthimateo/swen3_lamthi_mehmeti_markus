package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.CreateLabelRequest;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.entity.Label;
import com.swen3.swen3rest.mapper.LabelMapper;
import com.swen3.swen3rest.repository.LabelRepository;
import com.swen3.swen3rest.service.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LabelServiceImpl implements LabelService {

    private static final Logger log = LoggerFactory.getLogger(LabelServiceImpl.class);

    private final LabelRepository labelRepository;
    private final LabelMapper labelMapper;

    public LabelServiceImpl(LabelRepository labelRepository, LabelMapper labelMapper) {
        this.labelRepository = labelRepository;
        this.labelMapper = labelMapper;
    }

    @Override
    @Transactional
    public LabelDto create(CreateLabelRequest request) {
        // same rule as the UI: "Invoice" and "invoice" count as the same label
        if (labelRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("label '" + request.name() + "' already exists");
        }
        Label saved;
        try {
            saved = labelRepository.saveAndFlush(labelMapper.toEntity(request));
        } catch (DataIntegrityViolationException e) {
            // another request inserted the same name between the check above and this insert;
            // the unique constraint on labels.name caught it
            throw new ConflictException("label '" + request.name() + "' already exists", e);
        }
        log.info("Created label {} '{}'", saved.getId(), saved.getName());
        return labelMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabelDto> findAll() {
        log.debug("Listing all labels");
        return labelMapper.toDtos(labelRepository.findAll(Sort.by("name")));
    }
}
