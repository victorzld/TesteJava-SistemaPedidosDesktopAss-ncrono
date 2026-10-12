package br.com.pedidos.backend.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ErroApi")
public record ErroApi(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String codigo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mensagem,
        List<String> detalhes
) {
}
