package br.com.pedidos.desktop.http;

public class PedidoApiException extends Exception {

    private final Integer statusCode;

    public PedidoApiException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public PedidoApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
