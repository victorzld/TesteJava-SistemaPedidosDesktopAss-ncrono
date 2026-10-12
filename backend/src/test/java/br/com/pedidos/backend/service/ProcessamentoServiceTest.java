package br.com.pedidos.backend.service;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.exception.ExcecaoDeProcessamento;
import br.com.pedidos.backend.domain.exception.ProcessamentoInterrompidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessamentoServiceTest {

    @Mock
    private RandomGenerator random;

    @Mock
    private EsperaProcessamento espera;

    @Test
    void aguardaTempoSorteadoEAceitaLimiteDeVintePorCento() throws InterruptedException {
        when(random.nextLong(1, 4)).thenReturn(2L);
        when(random.nextDouble()).thenReturn(0.2);
        ProcessamentoService service = new ProcessamentoService(random, espera);

        assertThatCode(() -> service.processar(pedido())).doesNotThrowAnyException();

        ArgumentCaptor<Duration> duracao = ArgumentCaptor.forClass(Duration.class);
        verify(espera).aguardar(duracao.capture());
        assertThat(duracao.getValue()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void lancaFalhaDeNegocioQuandoSorteioForMenorQueVintePorCento() {
        when(random.nextLong(1, 4)).thenReturn(1L);
        when(random.nextDouble()).thenReturn(0.199999);
        ProcessamentoService service = new ProcessamentoService(random, espera);

        assertThatThrownBy(() -> service.processar(pedido()))
                .isInstanceOf(ExcecaoDeProcessamento.class)
                .hasMessageContaining(pedido().id().toString());
    }

    @Test
    void interrupcaoEInfraestruturaEPreservaFlagDaThread() throws InterruptedException {
        when(random.nextLong(1, 4)).thenReturn(3L);
        doThrow(new InterruptedException("encerrando"))
                .when(espera).aguardar(Duration.ofSeconds(3));
        ProcessamentoService service = new ProcessamentoService(random, espera);

        try {
            assertThatThrownBy(() -> service.processar(pedido()))
                    .isInstanceOf(ProcessamentoInterrompidoException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private Pedido pedido() {
        return new Pedido(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "Teclado",
                2,
                LocalDateTime.of(2026, 10, 12, 10, 30)
        );
    }
}
