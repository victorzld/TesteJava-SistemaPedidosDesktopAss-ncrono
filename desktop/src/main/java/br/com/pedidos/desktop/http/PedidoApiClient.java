package br.com.pedidos.desktop.http;

import br.com.pedidos.desktop.dto.ErroApi;
import br.com.pedidos.desktop.dto.PedidoAceitoResponse;
import br.com.pedidos.desktop.dto.PedidoRequest;
import br.com.pedidos.desktop.dto.StatusResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class PedidoApiClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final URI baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public PedidoApiClient(URI baseUrl) {
        this(
                baseUrl,
                HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build(),
                JsonMapper.builder().build(),
                DEFAULT_TIMEOUT
        );
    }

    public PedidoApiClient(
            URI baseUrl,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Duration requestTimeout
    ) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    public PedidoAceitoResponse enviarPedido(PedidoRequest pedido) throws PedidoApiException {
        Objects.requireNonNull(pedido, "pedido");
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint("/api/pedidos"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pedido)))
                    .build();
        } catch (JacksonException exception) {
            throw new PedidoApiException("Não foi possível preparar o pedido.", exception);
        }

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 202) {
            throw httpError(response);
        }
        return readBody(response.body(), PedidoAceitoResponse.class);
    }

    public StatusResponse consultarStatus(UUID id) throws PedidoApiException {
        Objects.requireNonNull(id, "id");
        HttpRequest request = HttpRequest.newBuilder(endpoint("/api/pedidos/status/" + id))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw httpError(response);
        }
        return readBody(response.body(), StatusResponse.class);
    }

    private HttpResponse<String> send(HttpRequest request) throws PedidoApiException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw new PedidoApiException("A API demorou demais para responder. Tente novamente.", exception);
        } catch (ConnectException exception) {
            throw new PedidoApiException("Não foi possível conectar à API. Verifique se o backend está ativo.", exception);
        } catch (IOException exception) {
            throw new PedidoApiException("Falha de comunicação com a API. Verifique sua conexão.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PedidoApiException("A comunicação com a API foi interrompida.", exception);
        }
    }

    private PedidoApiException httpError(HttpResponse<String> response) {
        String detail = parseErrorMessage(response.body());
        String message = switch (response.statusCode()) {
            case 400 -> "Pedido inválido: " + detail;
            case 404 -> "Pedido não encontrado no backend: " + detail;
            case 409 -> "Este pedido já foi registrado: " + detail;
            case 503 -> "O serviço de pedidos está temporariamente indisponível: " + detail;
            default -> "A API respondeu com HTTP " + response.statusCode() + ": " + detail;
        };
        return new PedidoApiException(message, response.statusCode());
    }

    private String parseErrorMessage(String body) {
        try {
            ErroApi error = objectMapper.readValue(body, ErroApi.class);
            if (error.mensagem() != null && !error.mensagem().isBlank()) {
                return error.mensagem();
            }
        } catch (JacksonException ignored) {
            // A resposta HTTP ainda é preservada como erro mesmo que o corpo não siga o contrato.
        }
        return "resposta sem detalhes";
    }

    private <T> T readBody(String body, Class<T> type) throws PedidoApiException {
        try {
            return objectMapper.readValue(body, type);
        } catch (JacksonException exception) {
            throw new PedidoApiException("A API retornou uma resposta JSON inválida.", exception);
        }
    }

    private URI endpoint(String path) {
        return baseUrl.resolve(path);
    }
}
