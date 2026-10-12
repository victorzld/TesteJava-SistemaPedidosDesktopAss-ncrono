package br.com.pedidos.backend.repository;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.RegistroPedido;
import br.com.pedidos.backend.domain.SituacaoPedido;
import br.com.pedidos.backend.domain.exception.PedidoDuplicadoException;
import br.com.pedidos.backend.domain.exception.PedidoNaoEncontradoException;
import br.com.pedidos.backend.domain.exception.TransicaoStatusInvalidaException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class PedidoEmMemoriaRepository {

    private final ConcurrentMap<UUID, RegistroPedido> pedidos = new ConcurrentHashMap<>();

    public RegistroPedido inserirRecebido(Pedido pedido) {
        RegistroPedido novoRegistro = RegistroPedido.recebido(pedido);
        RegistroPedido existente = pedidos.putIfAbsent(pedido.id(), novoRegistro);
        if (existente != null) {
            throw new PedidoDuplicadoException(pedido.id());
        }
        return novoRegistro;
    }

    public Optional<RegistroPedido> buscar(UUID id) {
        return Optional.ofNullable(pedidos.get(id));
    }

    public RegistroPedido atualizarStatus(
            UUID id,
            SituacaoPedido novoStatus,
            LocalDateTime dataProcessamento,
            String mensagemErro
    ) {
        return pedidos.compute(id, (chave, atual) -> {
            if (atual == null) {
                throw new PedidoNaoEncontradoException(chave);
            }
            if (atual.status().terminal()) {
                return atual;
            }
            if (!atual.status().permiteTransicaoPara(novoStatus)) {
                throw new TransicaoStatusInvalidaException(atual.status(), novoStatus);
            }
            return atual.comStatus(novoStatus, dataProcessamento, mensagemErro);
        });
    }

    public boolean removerReservaInalterada(RegistroPedido reserva) {
        return pedidos.remove(reserva.pedido().id(), reserva);
    }
}
