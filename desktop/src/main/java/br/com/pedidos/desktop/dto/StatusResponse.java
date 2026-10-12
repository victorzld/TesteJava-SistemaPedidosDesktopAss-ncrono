package br.com.pedidos.desktop.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record StatusResponse(
        UUID idPedido,
        SituacaoPedido status,
        LocalDateTime dataProcessamento,
        String mensagemErro
) {
}
