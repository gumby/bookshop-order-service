package com.polarbookshop.orderservice.order.web;

import com.polarbookshop.orderservice.web.OrderRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.Validator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


public class OrderRequestValidationTests {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void when_all_fields_correct_then_valid() {
        var orderRequest = new OrderRequest("1234567890", 1);
        Set<ConstraintViolation<OrderRequest>> violations = validator.validate(orderRequest);
        assertThat(violations).isEmpty();
    }

    @Test
    void when_isbn_not_defined_then_invalid() {
        var orderReqest = new OrderRequest("", 1);
        Set<ConstraintViolation<OrderRequest>> violations = validator.validate(orderReqest);
        assertThat(violations).hasSize(1);
        assertThat(violations.stream().iterator().next().getMessage())
                .isEqualTo("The book ISBN must be defined.");
    }

    @Test
    void when_quantity_not_defined_then_invalid() {
        var orderReqest = new OrderRequest("1234567890", null);
        Set<ConstraintViolation<OrderRequest>> violations = validator.validate(orderReqest);
        assertThat(violations).hasSize(1);
        assertThat(violations.stream().iterator().next().getMessage())
                .isEqualTo("The book quantity must be defined.");
    }

    @Test
    void when_quantity_lower_than_min_then_invalid() {
        var orderReqest = new OrderRequest("1234567890", 0);
        Set<ConstraintViolation<OrderRequest>> violations = validator.validate(orderReqest);
        assertThat(violations).hasSize(1);
        assertThat(violations.stream().iterator().next().getMessage())
                .isEqualTo("You must order at least 1 item.");
    }

    @Test
    void when_quantity_is_greater_than_max_then_invalid() {
        var orderReqest = new OrderRequest("1234567890", 17);
        Set<ConstraintViolation<OrderRequest>> violations = validator.validate(orderReqest);
        assertThat(violations).hasSize(1);
        assertThat(violations.stream().iterator().next().getMessage())
                .isEqualTo("You cannot order more than 5 items.");
    }
}
