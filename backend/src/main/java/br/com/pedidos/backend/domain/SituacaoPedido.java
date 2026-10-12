package br.com.pedidos.backend.domain;

public enum SituacaoPedido {
    RECEBIDO,
    PROCESSANDO,
    SUCESSO,
    FALHA;

    public boolean terminal() {
        return this == SUCESSO || this == FALHA;
    }

    public boolean permiteTransicaoPara(SituacaoPedido destino) {
        if (this == destino) {
            return true;
        }
        return switch (this) {
            case RECEBIDO -> destino == PROCESSANDO || destino.terminal();
            case PROCESSANDO -> destino.terminal();
            case SUCESSO, FALHA -> false;
        };
    }
}
