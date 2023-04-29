package com.polarbookshop.orderservice.order.web;

import com.polarbookshop.orderservice.web.OrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
public class OrderRequestJsonTests {

    @Autowired
    private JacksonTester<OrderRequest> json;

    @Test
    void test_deserialize() throws IOException {
        var content = """
                {
                    "isbn": "1234567890",
                    "quantity": 1
                }
                """;
        var expectedOrderRequest = new OrderRequest("1234567890", 1);

        assertThat(json.parse(content))
                .usingRecursiveComparison().isEqualTo(expectedOrderRequest);
    }

}
