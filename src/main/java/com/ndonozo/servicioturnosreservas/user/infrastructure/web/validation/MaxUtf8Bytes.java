package com.ndonozo.servicioturnosreservas.user.infrastructure.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {
    String message() default "no puede superar {value} bytes";
    int value();
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}