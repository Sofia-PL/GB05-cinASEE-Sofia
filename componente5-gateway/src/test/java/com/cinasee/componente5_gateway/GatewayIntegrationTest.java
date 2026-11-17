package com.cinasee.componente5_gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final String ISSUER = "http://gateway-test-issuer";
    private static final String KEY_ID = "gateway-integration-test-key";
    private static final String SUBJECT = "42";
    private static final RSAKey SIGNING_KEY = createSigningKey();
    private static final TestServer COMPONENT1 = backend("component1");
    private static final TestServer COMPONENT2 = backend("component2");
    private static final TestServer COMPONENT3 = backend("component3");
    private static final TestServer COMPONENT4 = backend("component4");
    private static final TestServer JWKS = startServer(exchange ->
            respond(exchange, 200, new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
    private static final List<TestServer> TEST_SERVERS =
            List.of(COMPONENT1, COMPONENT2, COMPONENT3, COMPONENT4, JWKS);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("COMPONENTE1_URI", COMPONENT1::uri);
        registry.add("COMPONENTE2_URI", COMPONENT2::uri);
        registry.add("COMPONENTE3_URI", COMPONENT3::uri);
        registry.add("COMPONENTE4_URI", COMPONENT4::uri);
        registry.add("app.security.jwt.jwk-set-uri", JWKS::uri);
        registry.add("app.security.jwt.issuer", () -> ISSUER);
    }

    @AfterAll
    static void stopTestServers() {
        TEST_SERVERS.forEach(TestServer::stop);
    }

    @Test
    void routesPublicCatalogAndLoginRequestsToTheirServices() throws Exception {
        HttpResponse<String> catalog = send("GET", "/api/v1/movies", null, null, null);
        HttpResponse<String> cinemas = send("GET", "/api/v1/cinemas", null, null, null);
        HttpResponse<String> login = send("POST", "/v1/iam/auth/login", null, null, null);

        assertEquals(200, catalog.statusCode());
        assertTrue(catalog.body().startsWith("component1|GET|/api/v1/movies|"));
        assertEquals(200, cinemas.statusCode());
        assertTrue(cinemas.body().startsWith("component1|GET|/api/v1/cinemas|"));
        assertEquals(200, login.statusCode());
        assertTrue(login.body().startsWith("component3|POST|/v1/iam/auth/login|"));
    }

    @Test
    void routesAuthenticatedRequestsAndForwardsTheBearerToken() throws Exception {
        String token = createToken(ISSUER, Instant.now().plusSeconds(300));

        HttpResponse<String> cinemaCreation = send("POST", "/api/v1/cinemas", token, null, null);
        HttpResponse<String> iam = send("GET", "/v1/iam/users/" + SUBJECT, token, null, null);
        HttpResponse<String> reservations = send("POST", "/v1/reservations", token, null, null);
        HttpResponse<String> recommendations =
                send("GET", "/api/recommendations/users/" + SUBJECT, token, null, null);

        assertEquals(200, cinemaCreation.statusCode());
        assertTrue(cinemaCreation.body().startsWith("component1|POST|/api/v1/cinemas|Bearer " + token));
        assertEquals(200, iam.statusCode());
        assertTrue(iam.body().startsWith("component3|GET|/v1/iam/users/" + SUBJECT + "|Bearer " + token));
        assertEquals(200, reservations.statusCode());
        assertTrue(reservations.body().startsWith("component2|POST|/v1/reservations|Bearer " + token));
        assertEquals(200, recommendations.statusCode());
        assertTrue(recommendations.body().startsWith(
                "component4|GET|/api/recommendations/users/" + SUBJECT + "|Bearer " + token));
    }

    @Test
    void rejectsMissingMalformedExpiredAndWrongIssuerTokens() throws Exception {
        HttpResponse<String> unauthenticatedCinemaCreation =
                send("POST", "/api/v1/cinemas", null, null, null);
        HttpResponse<String> missing = send("GET", "/api/recommendations/users/" + SUBJECT, null, null, null);
        HttpResponse<String> malformed =
                send("GET", "/api/recommendations/users/" + SUBJECT, "not-a-jwt", null, null);
        HttpResponse<String> expired =
                send("GET", "/api/recommendations/users/" + SUBJECT,
                        createToken(ISSUER, Instant.now().minusSeconds(60)), null, null);
        HttpResponse<String> wrongIssuer =
                send("GET", "/api/recommendations/users/" + SUBJECT,
                        createToken("http://wrong-issuer", Instant.now().plusSeconds(300)), null, null);

        assertEquals(401, unauthenticatedCinemaCreation.statusCode());
        assertEquals(401, missing.statusCode());
        assertEquals("Bearer", missing.headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals(401, malformed.statusCode());
        assertEquals(401, expired.statusCode());
        assertEquals(401, wrongIssuer.statusCode());
    }

    @Test
    void allowsCorsPreflightOnlyFromConfiguredFrontendOrigin() throws Exception {
        HttpResponse<String> allowed = send(
                "OPTIONS", "/api/v1/movies", null, "http://localhost:3000", "GET");
        HttpResponse<String> rejected = send(
                "OPTIONS", "/api/v1/movies", null, "http://malicious.example", "GET");

        assertEquals(200, allowed.statusCode());
        assertEquals("http://localhost:3000",
                allowed.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertTrue(allowed.headers().firstValue("Access-Control-Allow-Methods").orElse("")
                .contains("GET"));
        assertEquals(403, rejected.statusCode());
    }

    @Test
    void exposesHealthEndpointWithoutAuthentication() throws Exception {
        HttpResponse<String> response = send("GET", "/actuator/health", null, null, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
    }

    @Test
    void leavesUnmatchedPathsToNormalApplicationHandling() throws Exception {
        HttpResponse<String> response = send("GET", "/not-a-gateway-route", null, null, null);

        assertEquals(404, response.statusCode());
        assertFalse(response.body().startsWith("component"));
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String token,
            String origin,
            String requestedMethod
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path))
                .timeout(Duration.ofSeconds(10));

        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }
        if (requestedMethod != null) {
            request.header("Access-Control-Request-Method", requestedMethod);
        }

        HttpRequest.BodyPublisher body = "POST".equals(method)
                ? HttpRequest.BodyPublishers.ofString("{}")
                : HttpRequest.BodyPublishers.noBody();
        request.method(method, body);
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String createToken(String issuer, Instant expiresAt) {
        Instant now = Instant.now();
        Instant issuedAt = expiresAt.isBefore(now) ? expiresAt.minusSeconds(300) : now.minusSeconds(1);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(SUBJECT)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", "CLIENTE_REGISTRADO")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(KEY_ID)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY)))
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private static RSAKey createSigningKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(KEY_ID)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static TestServer backend(String name) {
        return startServer(exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String body = name + "|" + exchange.getRequestMethod() + "|"
                    + exchange.getRequestURI().getPath() + "|"
                    + (authorization == null ? "" : authorization);
            respond(exchange, 200, body);
        });
    }

    private static TestServer startServer(HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", handler);
            server.start();
            return new TestServer(server);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private record TestServer(HttpServer server) {
        private String uri() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void stop() {
            server.stop(0);
        }
    }
}
