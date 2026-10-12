package br.com.pedidos.backend.messaging;

import br.com.pedidos.backend.domain.StatusPedido;

public interface StatusPublisher {

    void publicar(StatusPedido statusPedido);
}
