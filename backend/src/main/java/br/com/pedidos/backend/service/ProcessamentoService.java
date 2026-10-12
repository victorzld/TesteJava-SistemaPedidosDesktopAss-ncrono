package br.com.pedidos.backend.service;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.exception.ExcecaoDeProcessamento;
import br.com.pedidos.backend.domain.exception.ProcessamentoInterrompidoException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.random.RandomGenerator;

@Service
public class ProcessamentoService {

    static final double PROBABILIDADE_DE_FALHA = 0.2;

    private final RandomGenerator random;
    private final EsperaProcessamento espera;

    public ProcessamentoService(RandomGenerator random, EsperaProcessamento espera) {
        this.random = random;
        this.espera = espera;
    }

    public void processar(Pedido pedido) {
        Duration duracao = Duration.ofSeconds(random.nextLong(1, 4));
        try {
            espera.aguardar(duracao);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProcessamentoInterrompidoException(
                    "Processamento do pedido " + pedido.id() + " foi interrompido",
                    exception
            );
        }

        if (random.nextDouble() < PROBABILIDADE_DE_FALHA) {
            throw new ExcecaoDeProcessamento("Falha simulada no processamento do pedido " + pedido.id());
        }
    }
}
