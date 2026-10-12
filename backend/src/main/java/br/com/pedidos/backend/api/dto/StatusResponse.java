package br.com.pedidos.backend.api.dto;

import br.com.pedidos.backend.domain.RegistroPedido;
import br.com.pedidos.backend.domain.SituacaoPedido;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "StatusConsulta")
public record StatusResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID idPedido,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SituacaoPedido status,
        LocalDateTime dataProcessamento,
        String mensagemErro
) {

    public static StatusResponse from(RegistroPedido registro) {
        return new StatusResponse(
                registro.pedido().id(),
                registro.status(),
                registro.dataProcessamento(),
                registro.mensagemErro()
        );
    }
}
