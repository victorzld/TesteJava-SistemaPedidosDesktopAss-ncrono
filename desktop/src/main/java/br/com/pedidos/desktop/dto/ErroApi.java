package br.com.pedidos.desktop.dto;

import java.util.List;

public record ErroApi(String codigo, String mensagem, List<String> detalhes) {
}
