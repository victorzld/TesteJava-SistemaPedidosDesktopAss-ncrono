package br.com.pedidos.backend.api.dto;

import br.com.pedidos.backend.domain.Pedido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "Pedido", description = "Pedido submetido para processamento assíncrono")
public record PedidoRequest(
        @NotNull(message = "ID do pedido é obrigatório")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid")
        UUID id,

        @NotBlank(message = "Produto é obrigatório")
        @Size(max = 200, message = "Produto deve ter no máximo 200 caracteres")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 200)
        String produto,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser maior que zero")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        Integer quantidade,

        @NotNull(message = "Data de criação é obrigatória")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-12T10:30:00")
        LocalDateTime dataCriacao
) {

    public Pedido toDomain() {
        return new Pedido(id, produto, quantidade, dataCriacao);
    }
}
