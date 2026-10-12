package br.com.pedidos.backend.domain.exception;

import br.com.pedidos.backend.domain.SituacaoPedido;

public class TransicaoStatusInvalidaException extends RuntimeException {

    public TransicaoStatusInvalidaException(SituacaoPedido origem, SituacaoPedido destino) {
        super("Transição de status inválida: " + origem + " -> " + destino);
    }
}
