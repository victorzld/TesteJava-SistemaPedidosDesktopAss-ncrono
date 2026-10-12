package br.com.pedidos.backend.service;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.RegistroPedido;
import br.com.pedidos.backend.domain.SituacaoPedido;
import br.com.pedidos.backend.domain.exception.PedidoDuplicadoException;
import br.com.pedidos.backend.domain.exception.PedidoInvalidoException;
import br.com.pedidos.backend.domain.exception.PedidoNaoEncontradoException;
import br.com.pedidos.backend.domain.exception.PublicacaoPedidoException;
import br.com.pedidos.backend.messaging.PedidoPublisher;
import br.com.pedidos.backend.repository.PedidoEmMemoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoPublisher publisher;

    private PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(new PedidoEmMemoriaRepository(), publisher);
    }

    @Test
    void aceitaPedidoComoRecebidoAposPublicacaoConfirmada() {
        Pedido pedido = pedidoValido(UUID.randomUUID());

        service.aceitar(pedido);

        RegistroPedido registro = service.consultar(pedido.id());
        assertThat(registro.status()).isEqualTo(SituacaoPedido.RECEBIDO);
        assertThat(registro.pedido()).isEqualTo(pedido);
        verify(publisher).publicar(pedido);
    }

    @Test
    void rejeitaUuidDuplicadoAntesDePublicarNovamente() {
        Pedido pedido = pedidoValido(UUID.randomUUID());
        service.aceitar(pedido);

        assertThatThrownBy(() -> service.aceitar(pedido))
                .isInstanceOf(PedidoDuplicadoException.class);
        verify(publisher).publicar(pedido);
    }

    @Test
    void atualizaStatusAtomicamenteESemRegredirTerminal() {
        Pedido pedido = pedidoValido(UUID.randomUUID());
        service.aceitar(pedido);

        service.atualizarStatus(pedido.id(), SituacaoPedido.PROCESSANDO, null, null);
        LocalDateTime concluidoEm = LocalDateTime.of(2026, 10, 12, 10, 30, 3);
        service.atualizarStatus(pedido.id(), SituacaoPedido.SUCESSO, concluidoEm, null);
        RegistroPedido aposTentativaDeRegressao = service.atualizarStatus(
                pedido.id(), SituacaoPedido.PROCESSANDO, null, null
        );

        assertThat(aposTentativaDeRegressao.status()).isEqualTo(SituacaoPedido.SUCESSO);
        assertThat(aposTentativaDeRegressao.dataProcessamento()).isEqualTo(concluidoEm);
    }

    @Test
    void liberaReservaQuandoPublicacaoFalhaSemResultadoAmbiguo() {
        Pedido pedido = pedidoValido(UUID.randomUUID());
        doThrow(new PublicacaoPedidoException("broker indisponível", false))
                .when(publisher).publicar(pedido);

        assertThatThrownBy(() -> service.aceitar(pedido))
                .isInstanceOf(PublicacaoPedidoException.class);
        assertThatThrownBy(() -> service.consultar(pedido.id()))
                .isInstanceOf(PedidoNaoEncontradoException.class);
    }

    @Test
    void preservaReservaQuandoResultadoDaPublicacaoEAmbiguo() {
        Pedido pedido = pedidoValido(UUID.randomUUID());
        doThrow(new PublicacaoPedidoException("timeout de confirmação", true))
                .when(publisher).publicar(pedido);

        assertThatThrownBy(() -> service.aceitar(pedido))
                .isInstanceOf(PublicacaoPedidoException.class);
        assertThat(service.consultar(pedido.id()).status()).isEqualTo(SituacaoPedido.RECEBIDO);
    }

    @Test
    void rejeitaDadosInvalidosNoDominio() {
        assertThatThrownBy(() -> new Pedido(
                UUID.randomUUID(), " ", 0, LocalDateTime.now()
        )).isInstanceOf(PedidoInvalidoException.class);
    }

    private Pedido pedidoValido(UUID id) {
        return new Pedido(id, "Teclado", 2, LocalDateTime.of(2026, 10, 12, 10, 30));
    }
}
