package br.com.pedidos.backend.service;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.RegistroPedido;
import br.com.pedidos.backend.domain.SituacaoPedido;
import br.com.pedidos.backend.domain.exception.PedidoNaoEncontradoException;
import br.com.pedidos.backend.domain.exception.PublicacaoPedidoException;
import br.com.pedidos.backend.messaging.PedidoPublisher;
import br.com.pedidos.backend.repository.PedidoEmMemoriaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PedidoService {

    private final PedidoEmMemoriaRepository repository;
    private final PedidoPublisher publisher;

    public PedidoService(PedidoEmMemoriaRepository repository, PedidoPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public void aceitar(Pedido pedido) {
        RegistroPedido reserva = repository.inserirRecebido(pedido);
        try {
            publisher.publicar(pedido);
        } catch (PublicacaoPedidoException exception) {
            if (!exception.resultadoAmbiguo()) {
                repository.removerReservaInalterada(reserva);
            }
            throw exception;
        } catch (RuntimeException exception) {
            repository.removerReservaInalterada(reserva);
            throw new PublicacaoPedidoException("Não foi possível publicar o pedido", false, exception);
        }
    }

    public RegistroPedido consultar(UUID id) {
        return repository.buscar(id).orElseThrow(() -> new PedidoNaoEncontradoException(id));
    }

    public RegistroPedido atualizarStatus(
            UUID id,
            SituacaoPedido novoStatus,
            LocalDateTime dataProcessamento,
            String mensagemErro
    ) {
        return repository.atualizarStatus(id, novoStatus, dataProcessamento, mensagemErro);
    }
}
