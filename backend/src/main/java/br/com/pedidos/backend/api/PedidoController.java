package br.com.pedidos.backend.api;

import br.com.pedidos.backend.api.dto.ErroApi;
import br.com.pedidos.backend.api.dto.PedidoAceitoResponse;
import br.com.pedidos.backend.api.dto.PedidoRequest;
import br.com.pedidos.backend.api.dto.StatusResponse;
import br.com.pedidos.backend.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private static final String MENSAGEM_ACEITE = "Pedido aceito para processamento assíncrono";

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    @Operation(summary = "Aceitar pedido para processamento assíncrono", operationId = "criarPedido")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Publicação confirmada e mensagem roteada",
                    content = @Content(schema = @Schema(implementation = PedidoAceitoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Pedido inválido ou JSON malformado",
                    content = @Content(schema = @Schema(implementation = ErroApi.class))),
            @ApiResponse(responseCode = "409", description = "UUID já registrado neste processo",
                    content = @Content(schema = @Schema(implementation = ErroApi.class))),
            @ApiResponse(responseCode = "503", description = "Publicação não confirmada ou indisponível",
                    content = @Content(schema = @Schema(implementation = ErroApi.class)))
    })
    public ResponseEntity<PedidoAceitoResponse> criar(@Valid @RequestBody PedidoRequest request) {
        pedidoService.aceitar(request.toDomain());
        PedidoAceitoResponse response = new PedidoAceitoResponse(request.id(), MENSAGEM_ACEITE);
        return ResponseEntity.accepted()
                .location(URI.create("/api/pedidos/status/" + request.id()))
                .body(response);
    }

    @GetMapping("/status/{id}")
    @Operation(summary = "Consultar status em memória", operationId = "obterStatus")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado",
                    content = @Content(schema = @Schema(implementation = StatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "UUID em formato inválido",
                    content = @Content(schema = @Schema(implementation = ErroApi.class))),
            @ApiResponse(responseCode = "404", description = "UUID não localizado no mapa em memória",
                    content = @Content(schema = @Schema(implementation = ErroApi.class)))
    })
    public StatusResponse obterStatus(@PathVariable("id") UUID id) {
        return StatusResponse.from(pedidoService.consultar(id));
    }
}
