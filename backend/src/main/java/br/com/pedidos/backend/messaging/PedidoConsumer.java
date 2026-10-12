package br.com.pedidos.backend.messaging;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.RegistroPedido;
import br.com.pedidos.backend.domain.SituacaoPedido;
import br.com.pedidos.backend.domain.StatusPedido;
import br.com.pedidos.backend.domain.exception.ExcecaoDeProcessamento;
import br.com.pedidos.backend.service.PedidoService;
import br.com.pedidos.backend.service.ProcessamentoService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class PedidoConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PedidoConsumer.class);

    private final PedidoService pedidoService;
    private final ProcessamentoService processamentoService;
    private final StatusPublisher statusPublisher;
    private final Clock clock;

    public PedidoConsumer(
            PedidoService pedidoService,
            ProcessamentoService processamentoService,
            StatusPublisher statusPublisher,
            Clock clock
    ) {
        this.pedidoService = pedidoService;
        this.processamentoService = processamentoService;
        this.statusPublisher = statusPublisher;
        this.clock = clock;
    }

    @RabbitListener(
            queues = "${app.rabbitmq.queue.entrada}",
            ackMode = "MANUAL",
            concurrency = "1",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}"
    )
    public void consumir(
            @Payload Pedido pedido,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered
    ) throws IOException {
        LOGGER.info("Iniciando processamento do pedido id={} redelivered={}", pedido.id(), redelivered);

        RegistroPedido registro = pedidoService.consultar(pedido.id());
        if (registro.status().terminal()) {
            finalizarRedeliveryTerminal(registro.status(), channel, deliveryTag);
            return;
        }

        pedidoService.atualizarStatus(pedido.id(), SituacaoPedido.PROCESSANDO, null, null);
        try {
            processamentoService.processar(pedido);
            concluirComSucesso(pedido, channel, deliveryTag);
        } catch (ExcecaoDeProcessamento exception) {
            concluirComFalhaDeNegocio(pedido, exception, channel, deliveryTag);
        }
    }

    private void concluirComSucesso(Pedido pedido, Channel channel, long deliveryTag) throws IOException {
        LocalDateTime dataProcessamento = LocalDateTime.now(clock);
        statusPublisher.publicar(StatusPedido.sucesso(pedido.id(), dataProcessamento));
        pedidoService.atualizarStatus(pedido.id(), SituacaoPedido.SUCESSO, dataProcessamento, null);
        channel.basicAck(deliveryTag, false);
        LOGGER.info("Pedido processado com sucesso id={}", pedido.id());
    }

    private void concluirComFalhaDeNegocio(
            Pedido pedido,
            ExcecaoDeProcessamento exception,
            Channel channel,
            long deliveryTag
    ) throws IOException {
        LocalDateTime dataProcessamento = LocalDateTime.now(clock);
        StatusPedido status = StatusPedido.falha(pedido.id(), dataProcessamento, exception.getMessage());
        statusPublisher.publicar(status);
        pedidoService.atualizarStatus(
                pedido.id(), SituacaoPedido.FALHA, dataProcessamento, exception.getMessage()
        );
        channel.basicReject(deliveryTag, false);
        LOGGER.warn("Falha simulada no pedido id={}: {}", pedido.id(), exception.getMessage());
    }

    private void finalizarRedeliveryTerminal(
            SituacaoPedido status,
            Channel channel,
            long deliveryTag
    ) throws IOException {
        if (status == SituacaoPedido.FALHA) {
            channel.basicReject(deliveryTag, false);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }
}
