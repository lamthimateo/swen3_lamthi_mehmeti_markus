package com.swen3.swen3rest.service;

import com.swen3.swen3rest.dto.CreateLabelRequest;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.service.exception.ConflictException;

import java.util.List;

public interface LabelService {

    /** @throws ConflictException if a label with this name (ignoring case) already exists */
    LabelDto create(CreateLabelRequest request);

    /** All labels, sorted by name. */
    List<LabelDto> findAll();
}
