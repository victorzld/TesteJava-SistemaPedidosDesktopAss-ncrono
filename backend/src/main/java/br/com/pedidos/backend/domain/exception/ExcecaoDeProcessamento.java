package br.com.pedidos.backend.domain.exception;

public class ExcecaoDeProcessamento extends RuntimeException {

    public ExcecaoDeProcessamento(String message) {
        super(message);
    }
}
