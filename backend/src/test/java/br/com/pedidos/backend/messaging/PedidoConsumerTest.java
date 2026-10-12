package br.com.pedidos.backend.messaging;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.RegistroPedido;
import br.com.pedidos.backend.domain.SituacaoPedido;
import br.com.pedidos.backend.domain.StatusPedido;
import br.com.pedidos.backend.domain.exception.ExcecaoDeProcessamento;
import br.com.pedidos.backend.domain.exception.PublicacaoStatusException;
import br.com.pedidos.backend.service.PedidoService;
import br.com.pedidos.backend.service.ProcessamentoService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoConsumerTest {

    private static final long DELIVERY_TAG = 42L;
    private static final LocalDateTime DATA_PROCESSAMENTO = LocalDateTime.of(2026, 10, 12, 13, 0);

    @Mock
    private PedidoService pedidoService;

    @Mock
    private ProcessamentoService processamentoService;

    @Mock
    private StatusPublisher statusPublisher;

    @Mock
    private Channel channel;

    private Pedido pedido;
    private PedidoConsumer consumer;

    @BeforeEach
    void setUp() {
        pedido = new Pedido(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "Teclado",
                2,
                LocalDateTime.of(2026, 10, 12, 10, 30)
        );
        Clock clock = Clock.fixed(DATA_PROCESSAMENTO.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        consumer = new PedidoConsumer(pedidoService, processamentoService, statusPublisher, clock);
    }

    @Test
    void publicaSucessoAtualizaEstadoEAckSomenteDepoisDaConfirmacao() throws IOException {
        when(pedidoService.consultar(pedido.id())).thenReturn(registro(SituacaoPedido.RECEBIDO));

        consumer.consumir(pedido, channel, DELIVERY_TAG, false);

        ArgumentCaptor<StatusPedido> evento = ArgumentCaptor.forClass(StatusPedido.class);
        InOrder ordem = inOrder(pedidoService, processamentoService, statusPublisher, channel);
        ordem.verify(pedidoService).atualizarStatus(pedido.id(), SituacaoPedido.PROCESSANDO, null, null);
        ordem.verify(processamentoService).processar(pedido);
        ordem.verify(statusPublisher).publicar(evento.capture());
        ordem.verify(pedidoService).atualizarStatus(
                pedido.id(), SituacaoPedido.SUCESSO, DATA_PROCESSAMENTO, null
        );
        ordem.verify(channel).basicAck(DELIVERY_TAG, false);
        assertThat(evento.getValue()).isEqualTo(StatusPedido.sucesso(pedido.id(), DATA_PROCESSAMENTO));
    }

    @Test
    void publicaFalhaAtualizaEstadoERejeitaSemRequeue() throws IOException {
        String mensagem = "falha simulada";
        when(pedidoService.consultar(pedido.id())).thenReturn(registro(SituacaoPedido.RECEBIDO));
        doThrow(new ExcecaoDeProcessamento(mensagem)).when(processamentoService).processar(pedido);

        consumer.consumir(pedido, channel, DELIVERY_TAG, false);

        ArgumentCaptor<StatusPedido> evento = ArgumentCaptor.forClass(StatusPedido.class);
        InOrder ordem = inOrder(pedidoService, processamentoService, statusPublisher, channel);
        ordem.verify(pedidoService).atualizarStatus(pedido.id(), SituacaoPedido.PROCESSANDO, null, null);
        ordem.verify(processamentoService).processar(pedido);
        ordem.verify(statusPublisher).publicar(evento.capture());
        ordem.verify(pedidoService).atualizarStatus(
                pedido.id(), SituacaoPedido.FALHA, DATA_PROCESSAMENTO, mensagem
        );
        ordem.verify(channel).basicReject(DELIVERY_TAG, false);
        assertThat(evento.getValue()).isEqualTo(StatusPedido.falha(pedido.id(), DATA_PROCESSAMENTO, mensagem));
        verify(channel, never()).basicAck(anyLong(), eq(false));
    }

    @Test
    void falhaAoPublicarSucessoNaoFinalizaEstadoNemConfirmaOriginal() throws IOException {
        when(pedidoService.consultar(pedido.id())).thenReturn(registro(SituacaoPedido.RECEBIDO));
        doThrow(new PublicacaoStatusException("broker indisponível", false))
                .when(statusPublisher).publicar(any(StatusPedido.class));

        assertThatThrownBy(() -> consumer.consumir(pedido, channel, DELIVERY_TAG, false))
                .isInstanceOf(PublicacaoStatusException.class);

        verify(pedidoService, never()).atualizarStatus(
                eq(pedido.id()), eq(SituacaoPedido.SUCESSO), any(LocalDateTime.class), eq(null)
        );
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void falhaAoPublicarEventoDeFalhaNaoRejeitaOriginal() throws IOException {
        when(pedidoService.consultar(pedido.id())).thenReturn(registro(SituacaoPedido.PROCESSANDO));
        doThrow(new ExcecaoDeProcessamento("falha simulada"))
                .when(processamentoService).processar(pedido);
        doThrow(new PublicacaoStatusException("timeout", true))
                .when(statusPublisher).publicar(any(StatusPedido.class));

        assertThatThrownBy(() -> consumer.consumir(pedido, channel, DELIVERY_TAG, true))
                .isInstanceOf(PublicacaoStatusException.class);

        verify(pedidoService, never()).atualizarStatus(
                eq(pedido.id()), eq(SituacaoPedido.FALHA), any(LocalDateTime.class), any(String.class)
        );
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void redeliveryDeSucessoTerminalApenasConfirmaOriginal() throws IOException {
        when(pedidoService.consultar(pedido.id())).thenReturn(registro(SituacaoPedido.SUCESSO));

        consumer.consumir(pedido, channel, DELIVERY_TAG, true);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verifyNoInteractions(processamentoService, statusPublisher);
    }

    @Test
    void redeliveryDeFalhaTerminalRejeitaNovamenteParaGarantirDlq() throws IOException {
        when(pedidoService.consultar(pedido.id())).thenReturn(registro(SituacaoPedido.FALHA));

        consumer.consumir(pedido, channel, DELIVERY_TAG, true);

        verify(channel).basicReject(DELIVERY_TAG, false);
        verifyNoInteractions(processamentoService, statusPublisher);
    }

    private RegistroPedido registro(SituacaoPedido status) {
        LocalDateTime data = status.terminal() ? DATA_PROCESSAMENTO : null;
        String erro = status == SituacaoPedido.FALHA ? "falha simulada" : null;
        return new RegistroPedido(pedido, status, data, erro);
    }
}
