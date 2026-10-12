package br.com.pedidos.backend.domain;

import br.com.pedidos.backend.domain.exception.PedidoInvalidoException;

import java.time.LocalDateTime;
import java.util.UUID;

public record Pedido(UUID id, String produto, int quantidade, LocalDateTime dataCriacao) {

    public Pedido {
        if (id == null) {
            throw new PedidoInvalidoException("ID do pedido é obrigatório");
        }
        if (produto == null || produto.isBlank()) {
            throw new PedidoInvalidoException("Produto é obrigatório");
        }
        if (produto.length() > 200) {
            throw new PedidoInvalidoException("Produto deve ter no máximo 200 caracteres");
        }
        if (quantidade <= 0) {
            throw new PedidoInvalidoException("Quantidade deve ser maior que zero");
        }
        if (dataCriacao == null) {
            throw new PedidoInvalidoException("Data de criação é obrigatória");
        }
    }
}
