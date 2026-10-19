package com.swen3.swen3rest.mapper;

import com.swen3.swen3rest.dto.DocumentLabelDto;
import com.swen3.swen3rest.entity.DocumentLabel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

// uses LabelMapper for the nested label (DocumentLabel.label -> DocumentLabelDto.label)
@Mapper(config = MapStructConfig.class, uses = LabelMapper.class)
public interface DocumentLabelMapper {

    @Mapping(target = "documentId", source = "document.id")
    DocumentLabelDto toDto(DocumentLabel documentLabel);

    List<DocumentLabelDto> toDtos(List<DocumentLabel> documentLabels);
}
