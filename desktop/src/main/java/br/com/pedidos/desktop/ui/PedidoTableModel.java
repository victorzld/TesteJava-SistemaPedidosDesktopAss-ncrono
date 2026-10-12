package br.com.pedidos.desktop.ui;

import br.com.pedidos.desktop.dto.PedidoRequest;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PedidoTableModel extends AbstractTableModel {

    public static final String STATUS_ENVIADO = "ENVIADO, AGUARDANDO PROCESSO";
    private static final String[] COLUMNS = {"ID", "Produto", "Quantidade", "Status"};

    private final Map<UUID, PedidoRow> rowsById = new LinkedHashMap<>();
    private final List<UUID> rowOrder = new ArrayList<>();

    @Override
    public int getRowCount() {
        return rowOrder.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 2 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        PedidoRow row = rowsById.get(rowOrder.get(rowIndex));
        return switch (columnIndex) {
            case 0 -> row.id().toString();
            case 1 -> row.produto();
            case 2 -> row.quantidade();
            case 3 -> row.status();
            default -> throw new IndexOutOfBoundsException("Coluna inválida: " + columnIndex);
        };
    }

    public void upsertPedido(PedidoRequest pedido, String status) {
        requireEdt();
        PedidoRow newRow = new PedidoRow(pedido.id(), pedido.produto(), pedido.quantidade(), status);
        PedidoRow previous = rowsById.put(pedido.id(), newRow);
        if (previous == null) {
            rowOrder.add(pedido.id());
            int inserted = rowOrder.size() - 1;
            fireTableRowsInserted(inserted, inserted);
        } else {
            int updated = rowOrder.indexOf(pedido.id());
            fireTableRowsUpdated(updated, updated);
        }
    }

    public void atualizarStatus(UUID id, String status) {
        requireEdt();
        PedidoRow current = rowsById.get(id);
        if (current == null) {
            return;
        }
        rowsById.put(id, new PedidoRow(id, current.produto(), current.quantidade(), status));
        int updated = rowOrder.indexOf(id);
        fireTableRowsUpdated(updated, updated);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("O modelo da tabela só pode ser alterado na EDT");
        }
    }

    private record PedidoRow(UUID id, String produto, int quantidade, String status) {
    }
}
