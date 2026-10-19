package com.swen3.swen3rest.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared settings for all mappers:
 * - SPRING: the generated implementation is a Spring bean, so services get it injected
 * - CONSTRUCTOR: mappers that use other mappers receive them via the constructor
 * - ERROR: a target field nobody maps breaks the build instead of silently staying null
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MapStructConfig {
}
