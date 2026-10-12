package br.com.pedidos.desktop.ui;

import br.com.pedidos.desktop.dto.PedidoRequest;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PedidoTableModelTest {

    @Test
    void upsertsOneRowPerUuidAndFiresEventsOnEdt() throws Exception {
        PedidoTableModel model = new PedidoTableModel();
        UUID id = UUID.randomUUID();
        PedidoRequest original = new PedidoRequest(id, "Mouse", 1, LocalDateTime.now());
        PedidoRequest updated = new PedidoRequest(id, "Mouse sem fio", 2, LocalDateTime.now());
        AtomicBoolean listenerRanOnEdt = new AtomicBoolean();
        AtomicReference<TableModelEvent> lastEvent = new AtomicReference<>();
        model.addTableModelListener(event -> {
            listenerRanOnEdt.set(SwingUtilities.isEventDispatchThread());
            lastEvent.set(event);
        });

        SwingUtilities.invokeAndWait(() -> {
            model.upsertPedido(original, PedidoTableModel.STATUS_ENVIADO);
            model.upsertPedido(updated, "PROCESSANDO");
            model.atualizarStatus(id, "SUCESSO");
        });

        assertEquals(1, model.getRowCount());
        assertEquals("Mouse sem fio", model.getValueAt(0, 1));
        assertEquals(2, model.getValueAt(0, 2));
        assertEquals("SUCESSO", model.getValueAt(0, 3));
        assertEquals(TableModelEvent.UPDATE, lastEvent.get().getType());
        assertTrue(listenerRanOnEdt.get());
    }

    @Test
    void rejectsMutationOutsideEdt() {
        PedidoTableModel model = new PedidoTableModel();
        PedidoRequest pedido = new PedidoRequest(UUID.randomUUID(), "Mouse", 1, LocalDateTime.now());

        assertThrows(
                IllegalStateException.class,
                () -> model.upsertPedido(pedido, PedidoTableModel.STATUS_ENVIADO)
        );
    }
}
