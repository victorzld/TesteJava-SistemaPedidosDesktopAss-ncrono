package br.com.pedidos.backend.messaging;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.exception.PublicacaoPedidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PedidoPublisherTest {

    private static final String EXCHANGE = "pedidos.exchange.luiz-victor";
    private static final String ROUTING_KEY = "pedidos.entrada";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitPedidoPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = publisherComTimeout(Duration.ofMillis(100));
    }

    @Test
    void publicaJsonPersistenteNoExchangeERoutingCorretosQuandoRecebeAck() {
        Pedido pedido = pedidoValido();
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class), any(CorrelationData.class));

        publisher.publicar(pedido);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).send(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                messageCaptor.capture(),
                correlationCaptor.capture()
        );
        Message message = messageCaptor.getValue();
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"id\":\"" + pedido.id() + "\"")
                .contains("\"produto\":\"Teclado\"")
                .contains("\"quantidade\":2")
                .contains("\"dataCriacao\":\"2026-10-12T10:30:00\"");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(pedido.id().toString());
        assertThat(correlationCaptor.getValue().getId()).isEqualTo(pedido.id().toString());
    }

    @Test
    void rejeitaNackDoBroker() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "sem espaço"));
            return null;
        }).when(rabbitTemplate).send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publicar(pedidoValido()))
                .isInstanceOf(PublicacaoPedidoException.class)
                .hasMessageContaining("sem espaço")
                .extracting("resultadoAmbiguo")
                .isEqualTo(false);
    }

    @Test
    void rejeitaMensagemRetornadaSemRotaMesmoComAck() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            Message message = invocation.getArgument(2);
            correlationData.setReturned(new ReturnedMessage(
                    message,
                    312,
                    "NO_ROUTE",
                    EXCHANGE,
                    ROUTING_KEY
            ));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publicar(pedidoValido()))
                .isInstanceOf(PublicacaoPedidoException.class)
                .hasMessageContaining("NO_ROUTE")
                .extracting("resultadoAmbiguo")
                .isEqualTo(false);
    }

    @Test
    void trataTimeoutComoResultadoAmbiguo() {
        RabbitPedidoPublisher publisherComTimeoutCurto = publisherComTimeout(Duration.ofMillis(1));

        assertThatThrownBy(() -> publisherComTimeoutCurto.publicar(pedidoValido()))
                .isInstanceOf(PublicacaoPedidoException.class)
                .hasMessageContaining("pode ter ocorrido")
                .extracting("resultadoAmbiguo")
                .isEqualTo(true);
    }

    @Test
    void converteFalhaSincronaDeComunicacaoEmPublicacaoNaoAmbigua() {
        doThrow(new AmqpException("conexão recusada"))
                .when(rabbitTemplate)
                .send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publicar(pedidoValido()))
                .isInstanceOf(PublicacaoPedidoException.class)
                .hasMessageContaining("RabbitMQ indisponível")
                .extracting("resultadoAmbiguo")
                .isEqualTo(false);
    }

    @Test
    void trataFalhaAssincronaDaConfirmacaoComoAmbigua() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().completeExceptionally(new IllegalStateException("canal encerrado"));
            return null;
        }).when(rabbitTemplate).send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publicar(pedidoValido()))
                .isInstanceOf(PublicacaoPedidoException.class)
                .hasMessageContaining("resultado é ambíguo")
                .extracting("resultadoAmbiguo")
                .isEqualTo(true);
    }

    private RabbitPedidoPublisher publisherComTimeout(Duration timeout) {
        return new RabbitPedidoPublisher(
                rabbitTemplate,
                new JacksonJsonMessageConverter("br.com.pedidos.backend"),
                EXCHANGE,
                ROUTING_KEY,
                timeout
        );
    }

    private Pedido pedidoValido() {
        return new Pedido(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "Teclado",
                2,
                LocalDateTime.of(2026, 10, 12, 10, 30)
        );
    }
}
