package br.com.pedidos.backend.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMqConfigTest {

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    void declaraExchangesFilasDuraveisBindingsEDlq() {
        Declarables declarables = config.pedidosTopology(
                "pedidos.exchange.luiz-victor",
                "pedidos.status.exchange.luiz-victor",
                "pedidos.dlx.luiz-victor",
                "pedidos.entrada.luiz-victor",
                "pedidos.status.sucesso.luiz-victor",
                "pedidos.status.falha.luiz-victor",
                "pedidos.entrada.luiz-victor.dlq",
                "pedidos.entrada",
                "pedidos.status.sucesso",
                "pedidos.status.falha",
                "pedidos.entrada.dlq"
        );

        List<DirectExchange> exchanges = declarables.getDeclarablesByType(DirectExchange.class);
        List<Queue> queues = declarables.getDeclarablesByType(Queue.class);
        List<Binding> bindings = declarables.getDeclarablesByType(Binding.class);

        assertThat(exchanges).extracting(DirectExchange::getName).containsExactlyInAnyOrder(
                "pedidos.exchange.luiz-victor",
                "pedidos.status.exchange.luiz-victor",
                "pedidos.dlx.luiz-victor"
        );
        assertThat(exchanges).allMatch(DirectExchange::isDurable);
        assertThat(queues).extracting(Queue::getName).containsExactlyInAnyOrder(
                "pedidos.entrada.luiz-victor",
                "pedidos.status.sucesso.luiz-victor",
                "pedidos.status.falha.luiz-victor",
                "pedidos.entrada.luiz-victor.dlq"
        );
        assertThat(queues).allMatch(Queue::isDurable);

        Queue entrada = queues.stream()
                .filter(queue -> queue.getName().equals("pedidos.entrada.luiz-victor"))
                .findFirst()
                .orElseThrow();
        assertThat(entrada.getArguments()).containsAllEntriesOf(Map.of(
                "x-dead-letter-exchange", "pedidos.dlx.luiz-victor",
                "x-dead-letter-routing-key", "pedidos.entrada.dlq"
        ));

        Set<String> rotas = bindings.stream()
                .map(binding -> binding.getExchange() + "|" + binding.getRoutingKey() + "|" + binding.getDestination())
                .collect(Collectors.toSet());
        assertThat(rotas).containsExactlyInAnyOrder(
                "pedidos.exchange.luiz-victor|pedidos.entrada|pedidos.entrada.luiz-victor",
                "pedidos.status.exchange.luiz-victor|pedidos.status.sucesso|pedidos.status.sucesso.luiz-victor",
                "pedidos.status.exchange.luiz-victor|pedidos.status.falha|pedidos.status.falha.luiz-victor",
                "pedidos.dlx.luiz-victor|pedidos.entrada.dlq|pedidos.entrada.luiz-victor.dlq"
        );
    }

    @Test
    void usaConversorJackson3() {
        assertThat(config.jacksonJsonMessageConverter()).isExactlyInstanceOf(JacksonJsonMessageConverter.class);
    }

    @Test
    void configuraRabbitTemplateComoMandatoryComConversorJackson3() {
        JacksonJsonMessageConverter converter = config.jacksonJsonMessageConverter();
        RabbitTemplate rabbitTemplate = config.rabbitTemplate(mock(ConnectionFactory.class), converter);

        assertThat(rabbitTemplate.getMessageConverter()).isSameAs(converter);
        assertThat(rabbitTemplate.isMandatoryFor(new Message(new byte[0], new MessageProperties()))).isTrue();
    }
}
