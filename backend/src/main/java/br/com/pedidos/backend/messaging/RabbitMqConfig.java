package br.com.pedidos.backend.messaging;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter("br.com.pedidos.backend");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter messageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(true);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }

    @Bean
    public Declarables pedidosTopology(
            @Value("${app.rabbitmq.exchange.entrada}") String exchangeEntradaNome,
            @Value("${app.rabbitmq.exchange.status}") String exchangeStatusNome,
            @Value("${app.rabbitmq.exchange.dlx}") String exchangeDlxNome,
            @Value("${app.rabbitmq.queue.entrada}") String filaEntradaNome,
            @Value("${app.rabbitmq.queue.sucesso}") String filaSucessoNome,
            @Value("${app.rabbitmq.queue.falha}") String filaFalhaNome,
            @Value("${app.rabbitmq.queue.dlq}") String filaDlqNome,
            @Value("${app.rabbitmq.routing.entrada}") String routingEntrada,
            @Value("${app.rabbitmq.routing.sucesso}") String routingSucesso,
            @Value("${app.rabbitmq.routing.falha}") String routingFalha,
            @Value("${app.rabbitmq.routing.dlq}") String routingDlq
    ) {
        DirectExchange exchangeEntrada = new DirectExchange(exchangeEntradaNome, true, false);
        DirectExchange exchangeStatus = new DirectExchange(exchangeStatusNome, true, false);
        DirectExchange exchangeDlx = new DirectExchange(exchangeDlxNome, true, false);

        Queue filaEntrada = QueueBuilder.durable(filaEntradaNome)
                .deadLetterExchange(exchangeDlxNome)
                .deadLetterRoutingKey(routingDlq)
                .build();
        Queue filaSucesso = QueueBuilder.durable(filaSucessoNome).build();
        Queue filaFalha = QueueBuilder.durable(filaFalhaNome).build();
        Queue filaDlq = QueueBuilder.durable(filaDlqNome).build();

        Binding bindingEntrada = BindingBuilder.bind(filaEntrada).to(exchangeEntrada).with(routingEntrada);
        Binding bindingSucesso = BindingBuilder.bind(filaSucesso).to(exchangeStatus).with(routingSucesso);
        Binding bindingFalha = BindingBuilder.bind(filaFalha).to(exchangeStatus).with(routingFalha);
        Binding bindingDlq = BindingBuilder.bind(filaDlq).to(exchangeDlx).with(routingDlq);

        return new Declarables(
                exchangeEntrada,
                exchangeStatus,
                exchangeDlx,
                filaEntrada,
                filaSucesso,
                filaFalha,
                filaDlq,
                bindingEntrada,
                bindingSucesso,
                bindingFalha,
                bindingDlq
        );
    }
}
