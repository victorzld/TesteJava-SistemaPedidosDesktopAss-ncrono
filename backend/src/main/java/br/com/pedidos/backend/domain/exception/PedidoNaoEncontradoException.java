package br.com.pedidos.backend.domain.exception;

import java.util.UUID;

public class PedidoNaoEncontradoException extends RuntimeException {

    public PedidoNaoEncontradoException(UUID id) {
        super("Pedido não encontrado: " + id);
    }
}
