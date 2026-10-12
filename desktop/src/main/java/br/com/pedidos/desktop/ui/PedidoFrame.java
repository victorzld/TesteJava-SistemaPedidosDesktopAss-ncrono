package br.com.pedidos.desktop.ui;

import br.com.pedidos.desktop.dto.PedidoAceitoResponse;
import br.com.pedidos.desktop.dto.PedidoRequest;
import br.com.pedidos.desktop.dto.StatusResponse;
import br.com.pedidos.desktop.http.PedidoApiClient;
import br.com.pedidos.desktop.http.PedidoApiException;
import br.com.pedidos.desktop.service.StatusPollingService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class PedidoFrame extends JFrame {

    private final JTextField produtoField = new JTextField(24);
    private final JTextField quantidadeField = new JTextField(8);
    private final JButton enviarButton = new JButton("Enviar Pedido");
    private final JLabel feedbackLabel = new JLabel("Pronto para enviar pedidos.");
    private final PedidoTableModel tableModel = new PedidoTableModel();
    private final PedidoApiClient apiClient;
    private final StatusPollingService pollingService;

    public PedidoFrame(PedidoApiClient apiClient) {
        super("Sistema de Pedidos");
        this.apiClient = apiClient;
        this.pollingService = new StatusPollingService(apiClient, this::statusReceived, this::pollingFailed);
        buildUi();
        pollingService.start();
    }

    private void buildUi() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Novo pedido"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.anchor = GridBagConstraints.WEST;

        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(new JLabel("Produto:"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        form.add(produtoField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        form.add(new JLabel("Quantidade:"), constraints);
        constraints.gridx = 1;
        form.add(quantidadeField, constraints);
        constraints.gridx = 2;
        form.add(enviarButton, constraints);

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(210);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(feedbackLabel);

        add(form, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        enviarButton.addActionListener(event -> submitFromFields());
        getRootPane().setDefaultButton(enviarButton);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                pollingService.close();
            }
        });

        setSize(850, 440);
        setLocationRelativeTo(null);
    }

    private void submitFromFields() {
        String produto = produtoField.getText().trim();
        if (produto.isEmpty()) {
            showValidationError("Informe o produto.");
            return;
        }
        if (produto.length() > 200) {
            showValidationError("O produto deve ter no máximo 200 caracteres.");
            return;
        }

        int quantidade;
        try {
            quantidade = Integer.parseInt(quantidadeField.getText().trim());
        } catch (NumberFormatException exception) {
            showValidationError("Informe uma quantidade inteira maior que zero.");
            return;
        }
        if (quantidade <= 0) {
            showValidationError("A quantidade deve ser maior que zero.");
            return;
        }

        PedidoRequest pedido = new PedidoRequest(UUID.randomUUID(), produto, quantidade, LocalDateTime.now());
        enviarButton.setEnabled(false);
        feedbackLabel.setText("Enviando pedido...");

        new SwingWorker<PedidoAceitoResponse, Void>() {
            @Override
            protected PedidoAceitoResponse doInBackground() throws PedidoApiException {
                return apiClient.enviarPedido(pedido);
            }

            @Override
            protected void done() {
                try {
                    PedidoAceitoResponse response = get();
                    if (!pedido.id().equals(response.id())) {
                        throw new PedidoApiException(
                                "A API confirmou um identificador diferente do enviado.",
                                (Integer) null
                        );
                    }
                    tableModel.upsertPedido(pedido, PedidoTableModel.STATUS_ENVIADO);
                    pollingService.acompanhar(pedido.id());
                    produtoField.setText("");
                    quantidadeField.setText("");
                    feedbackLabel.setText(response.mensagem());
                    produtoField.requestFocusInWindow();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showSubmissionError("O envio foi interrompido.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    showSubmissionError(cause == null ? "Falha inesperada ao enviar o pedido." : cause.getMessage());
                } catch (PedidoApiException exception) {
                    showSubmissionError(exception.getMessage());
                } finally {
                    enviarButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void statusReceived(UUID id, StatusResponse response) {
        tableModel.atualizarStatus(id, response.status().name());
        if (response.status().terminal()) {
            String suffix = response.mensagemErro() == null ? "" : " — " + response.mensagemErro();
            feedbackLabel.setText("Pedido " + id + ": " + response.status() + suffix);
        } else {
            feedbackLabel.setText("Pedido " + id + ": " + response.status());
        }
    }

    private void pollingFailed(UUID id, String message) {
        feedbackLabel.setText("Não foi possível atualizar " + id + ": " + message);
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(this, message, "Dados inválidos", JOptionPane.WARNING_MESSAGE);
    }

    private void showSubmissionError(String message) {
        String friendlyMessage = message == null || message.isBlank()
                ? "Não foi possível enviar o pedido."
                : message;
        feedbackLabel.setText(friendlyMessage);
        JOptionPane.showMessageDialog(this, friendlyMessage, "Erro ao enviar pedido", JOptionPane.ERROR_MESSAGE);
    }
}
