package br.com.pedidos.desktop.service;

import br.com.pedidos.desktop.dto.StatusResponse;
import br.com.pedidos.desktop.http.PedidoApiClient;
import br.com.pedidos.desktop.http.PedidoApiException;

import javax.swing.SwingUtilities;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class StatusPollingService implements AutoCloseable {

    private static final long POLLING_SECONDS = 3;

    private final StatusFetcher statusFetcher;
    private final ScheduledExecutorService scheduler;
    private final BiConsumer<UUID, StatusResponse> statusListener;
    private final BiConsumer<UUID, String> errorListener;
    private final Consumer<Runnable> uiDispatcher;
    private final long initialDelay;
    private final long interval;
    private final TimeUnit timeUnit;
    private final Set<UUID> pendingIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean started = new AtomicBoolean();

    public StatusPollingService(
            PedidoApiClient apiClient,
            BiConsumer<UUID, StatusResponse> statusListener,
            BiConsumer<UUID, String> errorListener
    ) {
        this(
                apiClient::consultarStatus,
                newDaemonScheduler(),
                statusListener,
                errorListener,
                SwingUtilities::invokeLater,
                POLLING_SECONDS,
                POLLING_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public StatusPollingService(
            StatusFetcher statusFetcher,
            ScheduledExecutorService scheduler,
            BiConsumer<UUID, StatusResponse> statusListener,
            BiConsumer<UUID, String> errorListener,
            Consumer<Runnable> uiDispatcher,
            long initialDelay,
            long interval,
            TimeUnit timeUnit
    ) {
        this.statusFetcher = statusFetcher;
        this.scheduler = scheduler;
        this.statusListener = statusListener;
        this.errorListener = errorListener;
        this.uiDispatcher = uiDispatcher;
        this.initialDelay = initialDelay;
        this.interval = interval;
        this.timeUnit = timeUnit;
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::pollSafely, initialDelay, interval, timeUnit);
        }
    }

    public void acompanhar(UUID id) {
        pendingIds.add(id);
    }

    public boolean estaPendente(UUID id) {
        return pendingIds.contains(id);
    }

    private void pollSafely() {
        for (UUID id : Set.copyOf(pendingIds)) {
            try {
                StatusResponse response = statusFetcher.consultar(id);
                if (response.status().terminal()) {
                    pendingIds.remove(id);
                }
                uiDispatcher.accept(() -> statusListener.accept(id, response));
            } catch (PedidoApiException exception) {
                uiDispatcher.accept(() -> errorListener.accept(id, exception.getMessage()));
            } catch (RuntimeException exception) {
                uiDispatcher.accept(() -> errorListener.accept(id, "Falha inesperada ao consultar o pedido."));
            }
        }
    }

    @Override
    public void close() {
        pendingIds.clear();
        scheduler.shutdownNow();
    }

    private static ScheduledExecutorService newDaemonScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pedido-status-polling");
            thread.setDaemon(true);
            return thread;
        });
    }

    @FunctionalInterface
    public interface StatusFetcher {
        StatusResponse consultar(UUID id) throws PedidoApiException;
    }
}
