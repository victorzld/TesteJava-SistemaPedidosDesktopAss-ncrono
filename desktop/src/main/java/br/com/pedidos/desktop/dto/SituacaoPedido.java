package br.com.pedidos.desktop.dto;

public enum SituacaoPedido {
    RECEBIDO,
    PROCESSANDO,
    SUCESSO,
    FALHA;

    public boolean terminal() {
        return this == SUCESSO || this == FALHA;
    }
}
