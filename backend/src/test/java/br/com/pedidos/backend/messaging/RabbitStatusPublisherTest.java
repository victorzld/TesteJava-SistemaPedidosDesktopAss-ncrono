package br.com.pedidos.backend.messaging;

import br.com.pedidos.backend.domain.StatusPedido;
import br.com.pedidos.backend.domain.exception.PublicacaoStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitStatusPublisherTest {

    private static final String EXCHANGE = "pedidos.status.exchange.luiz-victor";
    private static final String ROUTING_SUCESSO = "pedidos.status.sucesso";
    private static final String ROUTING_FALHA = "pedidos.status.falha";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitStatusPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = publisherComTimeout(Duration.ofMillis(100));
    }

    @Test
    void publicaSucessoComoJsonPersistenteNaRotaCorretaAposAck() {
        StatusPedido status = StatusPedido.sucesso(id(), dataProcessamento());
        responderComAck(ROUTING_SUCESSO);

        publisher.publicar(status);

        ArgumentCaptor<Message> mensagem = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(EXCHANGE), eq(ROUTING_SUCESSO), mensagem.capture(), any(CorrelationData.class)
        );
        String json = new String(mensagem.getValue().getBody(), StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"status\":\"SUCESSO\"")
                .contains("\"idPedido\":\"" + id() + "\"")
                .contains("\"dataProcessamento\":\"2026-10-12T13:00:00\"");
        assertThat(mensagem.getValue().getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void publicaFalhaNaRotaCorretaAposAck() {
        StatusPedido status = StatusPedido.falha(id(), dataProcessamento(), "falha simulada");
        responderComAck(ROUTING_FALHA);

        publisher.publicar(status);

        ArgumentCaptor<Message> mensagem = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(EXCHANGE), eq(ROUTING_FALHA), mensagem.capture(), any(CorrelationData.class)
        );
        assertThat(new String(mensagem.getValue().getBody(), StandardCharsets.UTF_8))
                .contains("\"status\":\"FALHA\"")
                .contains("\"mensagemErro\":\"falha simulada\"");
    }

    @Test
    void rejeitaNackDoBroker() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "sem espaço"));
            return null;
        }).when(rabbitTemplate).send(
                eq(EXCHANGE), eq(ROUTING_SUCESSO), any(Message.class), any(CorrelationData.class)
        );

        assertThatThrownBy(() -> publisher.publicar(StatusPedido.sucesso(id(), dataProcessamento())))
                .isInstanceOf(PublicacaoStatusException.class)
                .hasMessageContaining("sem espaço")
                .extracting("resultadoAmbiguo")
                .isEqualTo(false);
    }

    @Test
    void rejeitaEventoRetornadoSemRotaMesmoComAck() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            Message message = invocation.getArgument(2);
            correlation.setReturned(new ReturnedMessage(
                    message, 312, "NO_ROUTE", EXCHANGE, ROUTING_SUCESSO
            ));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq(EXCHANGE), eq(ROUTING_SUCESSO), any(Message.class), any(CorrelationData.class)
        );

        assertThatThrownBy(() -> publisher.publicar(StatusPedido.sucesso(id(), dataProcessamento())))
                .isInstanceOf(PublicacaoStatusException.class)
                .hasMessageContaining("NO_ROUTE");
    }

    @Test
    void timeoutDeConfirmacaoTemResultadoAmbiguo() {
        RabbitStatusPublisher publisherCurto = publisherComTimeout(Duration.ofMillis(1));

        assertThatThrownBy(() -> publisherCurto.publicar(StatusPedido.sucesso(id(), dataProcessamento())))
                .isInstanceOf(PublicacaoStatusException.class)
                .hasMessageContaining("pode ter ocorrido")
                .extracting("resultadoAmbiguo")
                .isEqualTo(true);
    }

    private void responderComAck(String routingKey) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq(EXCHANGE), eq(routingKey), any(Message.class), any(CorrelationData.class)
        );
    }

    private RabbitStatusPublisher publisherComTimeout(Duration timeout) {
        return new RabbitStatusPublisher(
                rabbitTemplate,
                new JacksonJsonMessageConverter("br.com.pedidos.backend"),
                EXCHANGE,
                ROUTING_SUCESSO,
                ROUTING_FALHA,
                timeout
        );
    }

    private UUID id() {
        return UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    }

    private LocalDateTime dataProcessamento() {
        return LocalDateTime.of(2026, 10, 12, 13, 0);
    }
}
