package br.com.pedidos.backend.domain.exception;

public class ProcessamentoInterrompidoException extends RuntimeException {

    public ProcessamentoInterrompidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
