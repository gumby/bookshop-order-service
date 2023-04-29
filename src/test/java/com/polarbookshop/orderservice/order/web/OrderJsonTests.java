package com.polarbookshop.orderservice.order.web;

import com.polarbookshop.orderservice.domain.Order;
import com.polarbookshop.orderservice.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
public class OrderJsonTests {

    @Autowired
    private JacksonTester<Order> json;

    @Test
    void test_serialize() throws IOException {
        var order = new Order(317L, "1234567890", "name", 9.90, 1, OrderStatus.ACCEPTED, Instant.now(), Instant.now(), 21);
        var jsonContent = json.write(order);
        assertThat(jsonContent).extractingJsonPathNumberValue("@.id")
                .isEqualTo(order.id().intValue());
        assertThat(jsonContent).extractingJsonPathStringValue("@.bookIsbn")
                .isEqualTo(order.bookIsbn());
        assertThat(jsonContent).extractingJsonPathStringValue("@.bookName")
                .isEqualTo(order.bookName());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.bookPrice")
                .isEqualTo(order.bookPrice());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.quantity")
                .isEqualTo(order.quantity());
        assertThat(jsonContent).extractingJsonPathStringValue("@.status")
                .isEqualTo(order.status().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.createdDate")
                .isEqualTo(order.createdDate().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.lastModifiedDate")
                .isEqualTo(order.lastModifiedDate().toString());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.version")
                .isEqualTo(order.version());
    }
}
