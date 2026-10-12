package br.com.pedidos.backend.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record StatusPedido(
        SituacaoPedido status,
        UUID idPedido,
        LocalDateTime dataProcessamento,
        String mensagemErro
) {

    public StatusPedido {
        Objects.requireNonNull(status, "Status é obrigatório");
        Objects.requireNonNull(idPedido, "ID do pedido é obrigatório");
        Objects.requireNonNull(dataProcessamento, "Data de processamento é obrigatória");
        if (!status.terminal()) {
            throw new IllegalArgumentException("Evento de status deve ser terminal");
        }
        if (status == SituacaoPedido.FALHA && (mensagemErro == null || mensagemErro.isBlank())) {
            throw new IllegalArgumentException("Mensagem de erro é obrigatória para falha");
        }
    }

    public static StatusPedido sucesso(UUID idPedido, LocalDateTime dataProcessamento) {
        return new StatusPedido(SituacaoPedido.SUCESSO, idPedido, dataProcessamento, null);
    }

    public static StatusPedido falha(UUID idPedido, LocalDateTime dataProcessamento, String mensagemErro) {
        return new StatusPedido(SituacaoPedido.FALHA, idPedido, dataProcessamento, mensagemErro);
    }
}
