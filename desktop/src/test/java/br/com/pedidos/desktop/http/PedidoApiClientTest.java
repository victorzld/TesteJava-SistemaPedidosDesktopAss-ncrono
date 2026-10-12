package br.com.pedidos.desktop.http;

import br.com.pedidos.desktop.dto.PedidoAceitoResponse;
import br.com.pedidos.desktop.dto.PedidoRequest;
import br.com.pedidos.desktop.dto.SituacaoPedido;
import br.com.pedidos.desktop.dto.StatusResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PedidoApiClientTest {

    @Test
    void postsContractJsonAndAccepts202() throws Exception {
        UUID id = UUID.randomUUID();
        StubHttpClient http = StubHttpClient.responding(202,
                "{\"id\":\"" + id + "\",\"mensagem\":\"Pedido aceito\"}");
        PedidoRequest request = new PedidoRequest(id, "Teclado", 2, LocalDateTime.of(2026, 10, 12, 10, 30));

        PedidoAceitoResponse response = client(http).enviarPedido(request);

        assertEquals(id, response.id());
        assertEquals("POST", http.lastRequest.method());
        assertEquals("/api/pedidos", http.lastRequest.uri().getPath());
        assertTrue(http.requestBody().contains("\"produto\":\"Teclado\""));
        assertTrue(http.requestBody().contains("\"quantidade\":2"));
        assertTrue(http.requestBody().contains("\"dataCriacao\":\"2026-10-12T10:30:00\""));
    }

    @Test
    void maps400ErrorBodyToFriendlyException() {
        StubHttpClient http = StubHttpClient.responding(400,
                "{\"codigo\":\"VALIDACAO\",\"mensagem\":\"Quantidade deve ser maior que zero\",\"detalhes\":[]}");
        PedidoRequest request = new PedidoRequest(UUID.randomUUID(), "Mouse", 0, LocalDateTime.now());

        PedidoApiException exception = assertThrows(PedidoApiException.class, () -> client(http).enviarPedido(request));

        assertEquals(400, exception.statusCode());
        assertTrue(exception.getMessage().contains("Quantidade deve ser maior que zero"));
    }

    @Test
    void maps503ErrorBodyToFriendlyException() {
        StubHttpClient http = StubHttpClient.responding(503,
                "{\"codigo\":\"PUBLICACAO_INDISPONIVEL\",\"mensagem\":\"Broker indisponível\",\"detalhes\":[]}");
        PedidoRequest request = new PedidoRequest(UUID.randomUUID(), "Mouse", 1, LocalDateTime.now());

        PedidoApiException exception = assertThrows(PedidoApiException.class, () -> client(http).enviarPedido(request));

        assertEquals(503, exception.statusCode());
        assertTrue(exception.getMessage().contains("temporariamente indisponível"));
    }

    @Test
    void maps409ErrorBodyToFriendlyException() {
        StubHttpClient http = StubHttpClient.responding(409,
                "{\"codigo\":\"PEDIDO_DUPLICADO\",\"mensagem\":\"UUID já registrado\",\"detalhes\":[]}");
        PedidoRequest request = new PedidoRequest(UUID.randomUUID(), "Mouse", 1, LocalDateTime.now());

        PedidoApiException exception = assertThrows(PedidoApiException.class, () -> client(http).enviarPedido(request));

        assertEquals(409, exception.statusCode());
        assertTrue(exception.getMessage().contains("já foi registrado"));
    }

    @Test
    void reportsRequestTimeout() {
        StubHttpClient http = StubHttpClient.failing(new HttpTimeoutException("timeout"));
        PedidoRequest request = new PedidoRequest(UUID.randomUUID(), "Mouse", 1, LocalDateTime.now());

        PedidoApiException exception = assertThrows(PedidoApiException.class, () -> client(http).enviarPedido(request));

        assertTrue(exception.getMessage().contains("demorou demais"));
    }

    @Test
    void reportsUnavailableNetwork() {
        StubHttpClient http = StubHttpClient.failing(new ConnectException("connection refused"));
        PedidoRequest request = new PedidoRequest(UUID.randomUUID(), "Mouse", 1, LocalDateTime.now());

        PedidoApiException exception = assertThrows(PedidoApiException.class, () -> client(http).enviarPedido(request));

        assertTrue(exception.getMessage().contains("conectar à API"));
    }

    @Test
    void getsAndParsesStatusContract() throws Exception {
        UUID id = UUID.randomUUID();
        StubHttpClient http = StubHttpClient.responding(200,
                "{\"idPedido\":\"" + id + "\",\"status\":\"SUCESSO\","
                        + "\"dataProcessamento\":\"2026-10-12T10:30:03\",\"mensagemErro\":null}");

        StatusResponse response = client(http).consultarStatus(id);

        assertEquals(id, response.idPedido());
        assertEquals(SituacaoPedido.SUCESSO, response.status());
        assertEquals(LocalDateTime.of(2026, 10, 12, 10, 30, 3), response.dataProcessamento());
        assertEquals("/api/pedidos/status/" + id, http.lastRequest.uri().getPath());
    }

    @Test
    void maps404WhenStatusIsNoLongerAvailable() {
        UUID id = UUID.randomUUID();
        StubHttpClient http = StubHttpClient.responding(404,
                "{\"codigo\":\"PEDIDO_NAO_ENCONTRADO\",\"mensagem\":\"Pedido não encontrado\",\"detalhes\":[]}");

        PedidoApiException exception = assertThrows(
                PedidoApiException.class,
                () -> client(http).consultarStatus(id)
        );

        assertEquals(404, exception.statusCode());
        assertTrue(exception.getMessage().contains("não encontrado"));
    }

    private PedidoApiClient client(HttpClient httpClient) {
        return new PedidoApiClient(
                URI.create("http://127.0.0.1:9090"),
                httpClient,
                JsonMapper.builder().build(),
                Duration.ofMillis(100)
        );
    }

    private static final class StubHttpClient extends HttpClient {
        private final Integer status;
        private final String body;
        private final IOException failure;
        private HttpRequest lastRequest;

        private StubHttpClient(Integer status, String body, IOException failure) {
            this.status = status;
            this.body = body;
            this.failure = failure;
        }

        static StubHttpClient responding(int status, String body) {
            return new StubHttpClient(status, body, null);
        }

        static StubHttpClient failing(IOException failure) {
            return new StubHttpClient(null, null, failure);
        }

        String requestBody() {
            return lastRequest.bodyPublisher().map(publisher -> {
                BodySubscriber subscriber = new BodySubscriber();
                publisher.subscribe(subscriber);
                return subscriber.body.join();
            }).orElse("");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            return (HttpResponse<T>) new StubResponse(status, body, request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(1)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }
    }

    private record StubResponse(int statusCode, String body, HttpRequest request) implements HttpResponse<String> {
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static final class BodySubscriber implements Flow.Subscriber<ByteBuffer> {
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private final StringBuilder content = new StringBuilder();

        @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        @Override public void onNext(ByteBuffer item) { content.append(StandardCharsets.UTF_8.decode(item)); }
        @Override public void onError(Throwable throwable) { body.completeExceptionally(throwable); }
        @Override public void onComplete() { body.complete(content.toString()); }
    }
}
