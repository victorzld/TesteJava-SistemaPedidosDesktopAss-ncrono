package br.com.pedidos.backend.messaging;

import br.com.pedidos.backend.domain.Pedido;

/**
 * Porta de saída. Retornar normalmente significa que o broker confirmou a
 * publicação e que a mensagem foi roteada; qualquer outro resultado deve lançar
 * uma exceção e nunca produzir HTTP 202.
 */
public interface PedidoPublisher {

    void publicar(Pedido pedido);
}
