package br.com.pedidos.desktop.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PedidoRequest(
        UUID id,
        String produto,
        int quantidade,
        LocalDateTime dataCriacao
) {
}
