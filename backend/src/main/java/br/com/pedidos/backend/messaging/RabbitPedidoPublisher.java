package br.com.pedidos.backend.messaging;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.exception.PublicacaoPedidoException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitPedidoPublisher implements PedidoPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final JacksonJsonMessageConverter messageConverter;
    private final String exchange;
    private final String routingKey;
    private final Duration confirmTimeout;

    @Autowired
    public RabbitPedidoPublisher(
            RabbitTemplate rabbitTemplate,
            JacksonJsonMessageConverter messageConverter,
            @Value("${app.rabbitmq.exchange.entrada}") String exchange,
            @Value("${app.rabbitmq.routing.entrada}") String routingKey,
            @Value("${app.publisher.confirm-timeout-seconds}") long confirmTimeoutSeconds
    ) {
        this(
                rabbitTemplate,
                messageConverter,
                exchange,
                routingKey,
                Duration.ofSeconds(confirmTimeoutSeconds)
        );
    }

    RabbitPedidoPublisher(
            RabbitTemplate rabbitTemplate,
            JacksonJsonMessageConverter messageConverter,
            String exchange,
            String routingKey,
            Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publicar(Pedido pedido) {
        CorrelationData correlationData = new CorrelationData(pedido.id().toString());
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(pedido.id().toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = messageConverter.toMessage(pedido, properties);

        try {
            rabbitTemplate.send(exchange, routingKey, message, correlationData);
        } catch (AmqpException exception) {
            throw new PublicacaoPedidoException("RabbitMQ indisponível durante a publicação", false, exception);
        }

        CorrelationData.Confirm confirm = aguardarConfirmacao(correlationData);
        if (!confirm.ack()) {
            throw new PublicacaoPedidoException(
                    "RabbitMQ rejeitou a publicação: " + motivoOuPadrao(confirm.reason()),
                    false
            );
        }
        if (correlationData.getReturned() != null) {
            throw new PublicacaoPedidoException(
                    "Mensagem não roteada: " + correlationData.getReturned().getReplyText(),
                    false
            );
        }
    }

    private CorrelationData.Confirm aguardarConfirmacao(CorrelationData correlationData) {
        try {
            return correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new PublicacaoPedidoException(
                    "Timeout aguardando confirmação do RabbitMQ; o aceite pode ter ocorrido",
                    true,
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PublicacaoPedidoException(
                    "Espera pela confirmação do RabbitMQ foi interrompida; o resultado é ambíguo",
                    true,
                    exception
            );
        } catch (ExecutionException exception) {
            throw new PublicacaoPedidoException(
                    "Falha ao obter confirmação do RabbitMQ; o resultado é ambíguo",
                    true,
                    exception.getCause()
            );
        }
    }

    private String motivoOuPadrao(String motivo) {
        return motivo == null || motivo.isBlank() ? "sem motivo informado" : motivo;
    }
}
