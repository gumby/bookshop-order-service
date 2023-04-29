package com.polarbookshop.orderservice;

import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import com.polarbookshop.orderservice.domain.Order;
import com.polarbookshop.orderservice.domain.OrderStatus;
import com.polarbookshop.orderservice.web.OrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderServiceApplicationTests {

	@Container
	static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:14.4");

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private BookClient bookClient;

	@DynamicPropertySource
	static void postgresqlProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.r2dbc.url", OrderServiceApplicationTests::r2dbcUrl);
		registry.add("spring.r2dbc.username", postgresql::getUsername);
		registry.add("spring.r2dbc.password", postgresql::getPassword);
		registry.add("spring.flyway.url", postgresql::getJdbcUrl);
	}

	private static String r2dbcUrl() {
		return String.format("r2dbc:postgresql://%s:%s/%s",
				postgresql.getHost(),
				postgresql.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
				postgresql.getDatabaseName());
	}

	@Test
	void when_get_orders_then_return() {
		var bookIsbn = "1234567893";
		var book = new Book(bookIsbn, "title", "author", 9.90);
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
		var orderReq = new OrderRequest(bookIsbn, 1);
		var expectedOrder = webTestClient.post().uri("/orders")
				.bodyValue(orderReq)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();
		assertThat(expectedOrder).isNotNull();

		webTestClient.get().uri("/orders")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBodyList(Order.class).value(orders -> {
					assertThat(orders.stream().filter(order -> order.bookIsbn().equals(bookIsbn)).findAny()).isNotEmpty();
				});
	}

	@Test
	void when_post_request_and_book_exists_then_order_accepted() {
		var bookIsbn = "1234567899";
		var book = new Book(bookIsbn, "title", "author", 9.90);
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
		var orderReq = new OrderRequest(bookIsbn, 3);

		var createdOrder = webTestClient.post().uri("/orders")
				.bodyValue(orderReq)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();

		assertThat(createdOrder).isNotNull();
		assertThat(createdOrder.bookIsbn()).isEqualTo(orderReq.isbn());
		assertThat(createdOrder.quantity()).isEqualTo(orderReq.quantity());
		assertThat(createdOrder.bookName()).isEqualTo(String.format("%s - %s", book.title(), book.author()));
		assertThat(createdOrder.bookPrice()).isEqualTo(book.price());
		assertThat(createdOrder.status()).isEqualTo(OrderStatus.ACCEPTED);
	}

	@Test
	void when_post_request_and_book_not_exists_then_order_rejected() {
		var bookIsbn = "1234567894";
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.empty());
		var orderRequest = new OrderRequest(bookIsbn, 3);

		var createdOrder = webTestClient.post().uri("/orders")
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();

		assertThat(createdOrder).isNotNull();
		assertThat(createdOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
		assertThat(createdOrder.quantity()).isEqualTo(orderRequest.quantity());
		assertThat(createdOrder.status()).isEqualTo(OrderStatus.REJECTED);
	}

}
