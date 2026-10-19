package com.swen3.swen3rest.mapper;

import com.swen3.swen3rest.dto.DocumentDto;
import com.swen3.swen3rest.entity.Document;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface DocumentMapper {

    DocumentDto toDto(Document document);
}
