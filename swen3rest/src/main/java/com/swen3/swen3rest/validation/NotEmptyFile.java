package com.swen3.swen3rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean Validation constraint for uploads: the multipart file must be present and contain at least one byte.
 * Standard constraints like @NotEmpty do not understand MultipartFile, hence this custom one.
 */
@Documented
@Constraint(validatedBy = NotEmptyFileValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotEmptyFile {

    String message() default "file must not be empty";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
