package br.com.pedidos.backend.domain;

import java.time.LocalDateTime;

public record RegistroPedido(
        Pedido pedido,
        SituacaoPedido status,
        LocalDateTime dataProcessamento,
        String mensagemErro
) {

    public static RegistroPedido recebido(Pedido pedido) {
        return new RegistroPedido(pedido, SituacaoPedido.RECEBIDO, null, null);
    }

    public RegistroPedido comStatus(
            SituacaoPedido novoStatus,
            LocalDateTime novaDataProcessamento,
            String novaMensagemErro
    ) {
        return new RegistroPedido(pedido, novoStatus, novaDataProcessamento, novaMensagemErro);
    }
}
