package br.com.pedidos.backend.domain.exception;

import java.util.UUID;

public class PedidoDuplicadoException extends RuntimeException {

    public PedidoDuplicadoException(UUID id) {
        super("Já existe um pedido com o ID " + id);
    }
}
