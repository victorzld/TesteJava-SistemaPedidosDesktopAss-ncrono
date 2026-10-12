package br.com.pedidos.backend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "PedidoAceito")
public record PedidoAceitoResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mensagem
) {
}
