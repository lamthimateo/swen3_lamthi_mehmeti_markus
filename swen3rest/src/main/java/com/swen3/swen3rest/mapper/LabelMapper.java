package com.swen3.swen3rest.mapper;

import com.swen3.swen3rest.dto.CreateLabelRequest;
import com.swen3.swen3rest.dto.LabelDto;
import com.swen3.swen3rest.entity.Label;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface LabelMapper {

    LabelDto toDto(Label label);

    List<LabelDto> toDtos(List<Label> labels);

    @Mapping(target = "id", ignore = true)   // assigned by the database on insert
    Label toEntity(CreateLabelRequest request);
}
