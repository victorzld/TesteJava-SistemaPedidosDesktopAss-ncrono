package br.com.pedidos.backend.domain.exception;

public class PublicacaoPedidoException extends RuntimeException {

    private final boolean resultadoAmbiguo;

    public PublicacaoPedidoException(String message, boolean resultadoAmbiguo) {
        super(message);
        this.resultadoAmbiguo = resultadoAmbiguo;
    }

    public PublicacaoPedidoException(String message, boolean resultadoAmbiguo, Throwable cause) {
        super(message, cause);
        this.resultadoAmbiguo = resultadoAmbiguo;
    }

    public boolean resultadoAmbiguo() {
        return resultadoAmbiguo;
    }
}
