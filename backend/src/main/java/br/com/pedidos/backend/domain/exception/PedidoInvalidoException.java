package br.com.pedidos.backend.domain.exception;

public class PedidoInvalidoException extends RuntimeException {

    public PedidoInvalidoException(String message) {
        super(message);
    }
}
