package br.com.pedidos.backend.service;

import java.time.Duration;

@FunctionalInterface
public interface EsperaProcessamento {

    void aguardar(Duration duracao) throws InterruptedException;
}
