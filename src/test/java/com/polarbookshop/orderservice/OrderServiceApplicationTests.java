package com.polarbookshop.orderservice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import com.polarbookshop.orderservice.domain.Order;
import com.polarbookshop.orderservice.domain.OrderStatus;
import com.polarbookshop.orderservice.order.event.OrderAcceptedMessage;
import com.polarbookshop.orderservice.web.OrderRequest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
@Testcontainers
class OrderServiceApplicationTests {

	private static KeycloakToken isabelleTokens;
	private static KeycloakToken bjornTokens;

	@Container
	private static final KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:19.0")
			.withRealmImportFile("test-realm-config.json");

	@Container
	static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:14.4");

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private BookClient bookClient;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OutputDestination output;

	@DynamicPropertySource
	static void postgresqlProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.r2dbc.url", OrderServiceApplicationTests::r2dbcUrl);
		registry.add("spring.r2dbc.username", postgresql::getUsername);
		registry.add("spring.r2dbc.password", postgresql::getPassword);
		registry.add("spring.flyway.url", postgresql::getJdbcUrl);

		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
				() -> keycloakContainer.getAuthServerUrl() + "realms/PolarBookshop");
	}

	private static String r2dbcUrl() {
		return String.format("r2dbc:postgresql://%s:%s/%s",
				postgresql.getHost(),
				postgresql.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
				postgresql.getDatabaseName());
	}

	@BeforeAll
	static void generateAccessTokens() {
		WebClient webClient = WebClient.builder()
				.baseUrl(keycloakContainer.getAuthServerUrl() + "realms/PolarBookshop/protocol/openid-connect/token")
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
				.build();

		isabelleTokens = authenticateWith("isabelle", "password", webClient);
		bjornTokens = authenticateWith("bjorn", "password", webClient);
	}

	@Test
	void whenGetOwnOrdersThenReturn() throws IOException {
		var bookIsbn = "1234567893";
		var book = new Book(bookIsbn, "title", "author", 9.90);
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
		var orderReq = new OrderRequest(bookIsbn, 1);

		var expectedOrder = webTestClient.post().uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.bodyValue(orderReq)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();
		assertThat(expectedOrder).isNotNull();
		assertThat(objectMapper.readValue(output.receive().getPayload(), OrderAcceptedMessage.class))
				.isEqualTo(new OrderAcceptedMessage(expectedOrder.id()));

		webTestClient.get().uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBodyList(Order.class).value(orders -> {
					assertThat(orders.stream().filter(order -> order.bookIsbn().equals(bookIsbn)).findAny()).isNotEmpty();
				});
	}

	@Test
	void when_post_request_and_book_exists_then_order_accepted() throws IOException {
		var bookIsbn = "1234567899";
		var book = new Book(bookIsbn, "title", "author", 9.90);
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
		var orderReq = new OrderRequest(bookIsbn, 3);

		var createdOrder = webTestClient.post().uri("/orders")
				.bodyValue(orderReq)
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class)
				.value(order -> {
					assertThat(order).isNotNull();
					assertThat(order.bookIsbn()).isEqualTo(orderReq.isbn());
					assertThat(order.quantity()).isEqualTo(orderReq.quantity());
					assertThat(order.bookName()).isEqualTo(String.format("%s - %s", book.title(), book.author()));
					assertThat(order.bookPrice()).isEqualTo(book.price());
					assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
				})
				.returnResult()
				.getResponseBody();

		assertThat(objectMapper.readValue(output.receive().getPayload(), OrderAcceptedMessage.class))
				.isEqualTo(new OrderAcceptedMessage(createdOrder.id()));
	}

	@Test
	void when_post_request_and_book_not_exists_then_order_rejected() {
		var bookIsbn = "1234567894";
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.empty());
		var orderRequest = new OrderRequest(bookIsbn, 3);

		webTestClient.post().uri("/orders")
				.headers(headers -> headers.setBearerAuth(bjornTokens.accessToken()))
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class)
				.value(order -> {
					assertThat(order).isNotNull();
					assertThat(order.bookIsbn()).isEqualTo(orderRequest.isbn());
					assertThat(order.quantity()).isEqualTo(orderRequest.quantity());
					assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
				});
	}

	@Test
	void contextLoads() {
	}

	private static KeycloakToken authenticateWith(String username, String password, WebClient webClient) {
		return webClient.post()
			.body(BodyInserters.fromFormData("grant_type", "password")
				.with("client_id", "polar-test")
				.with("username", username)
				.with("password", password)
			)
			.retrieve()
			.bodyToMono(KeycloakToken.class)
			.block();
	}

	private record KeycloakToken(String accessToken) {

		@JsonCreator
		private KeycloakToken(@JsonProperty("access_token") final String accessToken) {
			this.accessToken = accessToken;
		}
	}

}
