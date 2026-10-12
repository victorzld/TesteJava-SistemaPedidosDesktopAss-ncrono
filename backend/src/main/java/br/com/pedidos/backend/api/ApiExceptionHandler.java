package br.com.pedidos.backend.api;

import br.com.pedidos.backend.api.dto.ErroApi;
import br.com.pedidos.backend.domain.exception.PedidoDuplicadoException;
import br.com.pedidos.backend.domain.exception.PedidoInvalidoException;
import br.com.pedidos.backend.domain.exception.PedidoNaoEncontradoException;
import br.com.pedidos.backend.domain.exception.PublicacaoPedidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroApi> handleValidation(MethodArgumentNotValidException exception) {
        List<String> detalhes = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return resposta(HttpStatus.BAD_REQUEST, "VALIDACAO", "Pedido inválido", detalhes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroApi> handleUnreadableMessage() {
        return resposta(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "JSON inválido ou malformado", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroApi> handleTypeMismatch() {
        return resposta(HttpStatus.BAD_REQUEST, "UUID_INVALIDO", "UUID em formato inválido", List.of());
    }

    @ExceptionHandler(PedidoInvalidoException.class)
    public ResponseEntity<ErroApi> handleInvalidOrder(PedidoInvalidoException exception) {
        return resposta(HttpStatus.BAD_REQUEST, "VALIDACAO", exception.getMessage(), List.of());
    }

    @ExceptionHandler(PedidoDuplicadoException.class)
    public ResponseEntity<ErroApi> handleDuplicate(PedidoDuplicadoException exception) {
        return resposta(HttpStatus.CONFLICT, "PEDIDO_DUPLICADO", exception.getMessage(), List.of());
    }

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    public ResponseEntity<ErroApi> handleNotFound(PedidoNaoEncontradoException exception) {
        return resposta(HttpStatus.NOT_FOUND, "PEDIDO_NAO_ENCONTRADO", exception.getMessage(), List.of());
    }

    @ExceptionHandler(PublicacaoPedidoException.class)
    public ResponseEntity<ErroApi> handlePublication(PublicacaoPedidoException exception) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "PUBLICACAO_INDISPONIVEL", exception.getMessage(), List.of());
    }

    private ResponseEntity<ErroApi> resposta(
            HttpStatus status,
            String codigo,
            String mensagem,
            List<String> detalhes
    ) {
        return ResponseEntity.status(status).body(new ErroApi(codigo, mensagem, detalhes));
    }
}
