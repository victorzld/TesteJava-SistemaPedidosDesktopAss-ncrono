package br.com.pedidos.desktop;

import br.com.pedidos.desktop.http.PedidoApiClient;
import br.com.pedidos.desktop.ui.PedidoFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

public final class DesktopApplication {

    private DesktopApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DesktopApplication::showApplication);
    }

    private static void showApplication() {
        try {
            URI baseUrl = loadApiBaseUrl();
            new PedidoFrame(new PedidoApiClient(baseUrl)).setVisible(true);
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(
                    null,
                    "Não foi possível iniciar o aplicativo: " + exception.getMessage(),
                    "Erro de configuração",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static URI loadApiBaseUrl() {
        Properties properties = new Properties();
        try (InputStream input = DesktopApplication.class.getResourceAsStream("/desktop.properties")) {
            if (input == null) {
                throw new IllegalStateException("arquivo desktop.properties não encontrado");
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("falha ao ler desktop.properties", exception);
        }

        String configuredUrl = properties.getProperty("api.base-url");
        if (configuredUrl == null || configuredUrl.isBlank()) {
            throw new IllegalStateException("propriedade api.base-url não configurada");
        }
        return URI.create(configuredUrl.trim());
    }
}
