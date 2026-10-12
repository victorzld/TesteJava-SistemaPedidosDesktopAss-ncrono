package br.com.pedidos.desktop.service;

import br.com.pedidos.desktop.dto.SituacaoPedido;
import br.com.pedidos.desktop.dto.StatusResponse;
import br.com.pedidos.desktop.http.PedidoApiException;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusPollingServiceTest {

    @Test
    void pollsOnlyPendingAndStopsTerminalUuidWithControlledScheduler() {
        ControlledScheduler scheduler = new ControlledScheduler();
        UUID pending = UUID.randomUUID();
        UUID ignored = UUID.randomUUID();
        List<UUID> requested = new ArrayList<>();
        AtomicReference<UUID> updated = new AtomicReference<>();
        StatusPollingService service = new StatusPollingService(
                id -> {
                    requested.add(id);
                    return new StatusResponse(id, SituacaoPedido.SUCESSO, null, null);
                },
                scheduler,
                (id, status) -> updated.set(id),
                (id, error) -> { },
                Runnable::run,
                3,
                3,
                TimeUnit.SECONDS
        );

        service.acompanhar(pending);
        service.start();
        scheduler.runCapturedTask();

        assertEquals(List.of(pending), requested);
        assertFalse(requested.contains(ignored));
        assertEquals(pending, updated.get());
        assertFalse(service.estaPendente(pending));
        service.close();
        assertTrue(scheduler.isShutdown());
    }

    @Test
    void runsHttpOffCallerThreadAndDispatchesUpdateOnEdt() throws Exception {
        UUID id = UUID.randomUUID();
        String callerThread = Thread.currentThread().getName();
        AtomicBoolean callbackOnEdt = new AtomicBoolean();
        AtomicBoolean requestOnDifferentThread = new AtomicBoolean();
        CountDownLatch callback = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        StatusPollingService service = new StatusPollingService(
                requestedId -> {
                    requestOnDifferentThread.set(!Thread.currentThread().getName().equals(callerThread));
                    return new StatusResponse(requestedId, SituacaoPedido.PROCESSANDO, null, null);
                },
                scheduler,
                (updatedId, status) -> {
                    callbackOnEdt.set(SwingUtilities.isEventDispatchThread());
                    callback.countDown();
                },
                (failedId, error) -> callback.countDown(),
                SwingUtilities::invokeLater,
                0,
                1,
                TimeUnit.DAYS
        );

        try {
            service.acompanhar(id);
            service.start();
            assertTrue(callback.await(3, TimeUnit.SECONDS));
            assertTrue(requestOnDifferentThread.get());
            assertTrue(callbackOnEdt.get());
            assertTrue(service.estaPendente(id));
        } finally {
            service.close();
        }
    }

    @Test
    void keepsUuidPendingAfterTransientGetFailure() {
        ControlledScheduler scheduler = new ControlledScheduler();
        UUID id = UUID.randomUUID();
        AtomicReference<String> reportedError = new AtomicReference<>();
        StatusPollingService service = new StatusPollingService(
                requestedId -> {
                    throw new PedidoApiException("API temporariamente indisponível", 503);
                },
                scheduler,
                (updatedId, status) -> { },
                (failedId, error) -> reportedError.set(error),
                Runnable::run,
                3,
                3,
                TimeUnit.SECONDS
        );

        service.acompanhar(id);
        service.start();
        scheduler.runCapturedTask();

        assertTrue(service.estaPendente(id));
        assertEquals("API temporariamente indisponível", reportedError.get());
        service.close();
    }

    private static final class ControlledScheduler extends ScheduledThreadPoolExecutor {
        private Runnable capturedTask;

        private ControlledScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command,
                long initialDelay,
                long delay,
                TimeUnit unit
        ) {
            capturedTask = command;
            return new NeverScheduledFuture();
        }

        void runCapturedTask() {
            capturedTask.run();
        }
    }

    private static final class NeverScheduledFuture implements ScheduledFuture<Object> {
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return true; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return false; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
    }
}
