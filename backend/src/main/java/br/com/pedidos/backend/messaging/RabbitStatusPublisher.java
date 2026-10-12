package br.com.pedidos.backend.messaging;

import br.com.pedidos.backend.domain.SituacaoPedido;
import br.com.pedidos.backend.domain.StatusPedido;
import br.com.pedidos.backend.domain.exception.PublicacaoStatusException;
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
public class RabbitStatusPublisher implements StatusPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final JacksonJsonMessageConverter messageConverter;
    private final String exchange;
    private final String routingSucesso;
    private final String routingFalha;
    private final Duration confirmTimeout;

    @Autowired
    public RabbitStatusPublisher(
            RabbitTemplate rabbitTemplate,
            JacksonJsonMessageConverter messageConverter,
            @Value("${app.rabbitmq.exchange.status}") String exchange,
            @Value("${app.rabbitmq.routing.sucesso}") String routingSucesso,
            @Value("${app.rabbitmq.routing.falha}") String routingFalha,
            @Value("${app.publisher.confirm-timeout-seconds}") long confirmTimeoutSeconds
    ) {
        this(rabbitTemplate, messageConverter, exchange, routingSucesso, routingFalha,
                Duration.ofSeconds(confirmTimeoutSeconds));
    }

    RabbitStatusPublisher(
            RabbitTemplate rabbitTemplate,
            JacksonJsonMessageConverter messageConverter,
            String exchange,
            String routingSucesso,
            String routingFalha,
            Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.exchange = exchange;
        this.routingSucesso = routingSucesso;
        this.routingFalha = routingFalha;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publicar(StatusPedido statusPedido) {
        String routingKey = statusPedido.status() == SituacaoPedido.SUCESSO ? routingSucesso : routingFalha;
        String correlationId = statusPedido.idPedido() + ":" + statusPedido.status();
        CorrelationData correlationData = new CorrelationData(correlationId);
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(correlationId);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = messageConverter.toMessage(statusPedido, properties);

        try {
            rabbitTemplate.send(exchange, routingKey, message, correlationData);
        } catch (AmqpException exception) {
            throw new PublicacaoStatusException("RabbitMQ indisponível ao publicar status", false, exception);
        }

        CorrelationData.Confirm confirm = aguardarConfirmacao(correlationData);
        if (!confirm.ack()) {
            throw new PublicacaoStatusException(
                    "RabbitMQ rejeitou o status: " + motivoOuPadrao(confirm.reason()), false
            );
        }
        if (correlationData.getReturned() != null) {
            throw new PublicacaoStatusException(
                    "Evento de status não roteado: " + correlationData.getReturned().getReplyText(), false
            );
        }
    }

    private CorrelationData.Confirm aguardarConfirmacao(CorrelationData correlationData) {
        try {
            return correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new PublicacaoStatusException(
                    "Timeout aguardando confirmação do status; a publicação pode ter ocorrido", true, exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PublicacaoStatusException(
                    "Espera da confirmação do status foi interrompida; o resultado é ambíguo", true, exception
            );
        } catch (ExecutionException exception) {
            throw new PublicacaoStatusException(
                    "Falha ao confirmar publicação do status; o resultado é ambíguo", true, exception.getCause()
            );
        }
    }

    private String motivoOuPadrao(String motivo) {
        return motivo == null || motivo.isBlank() ? "sem motivo informado" : motivo;
    }
}
