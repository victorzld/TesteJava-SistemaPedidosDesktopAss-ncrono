package br.com.pedidos.backend.api;

import br.com.pedidos.backend.domain.Pedido;
import br.com.pedidos.backend.domain.RegistroPedido;
import br.com.pedidos.backend.domain.SituacaoPedido;
import br.com.pedidos.backend.domain.exception.PedidoDuplicadoException;
import br.com.pedidos.backend.domain.exception.PedidoNaoEncontradoException;
import br.com.pedidos.backend.domain.exception.PublicacaoPedidoException;
import br.com.pedidos.backend.repository.PedidoEmMemoriaRepository;
import br.com.pedidos.backend.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock
    private PedidoService pedidoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcPara(pedidoService);
    }

    private MockMvc mockMvcPara(PedidoService service) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new PedidoController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void retorna202SomenteQuandoServicoConfirmaAceite() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/pedidos")
                        .contentType("application/json")
                        .content(jsonValido(id)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/pedidos/status/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.mensagem").value("Pedido aceito para processamento assíncrono"));
    }

    @Test
    void retorna400ParaCamposInvalidos() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/pedidos")
                        .contentType("application/json")
                        .content("""
                                {"id":"%s","produto":" ","quantidade":0,"dataCriacao":null}
                                """.formatted(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.detalhes.length()").value(3));
    }

    @Test
    void retorna400ParaJsonMalformado() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .contentType("application/json")
                        .content("{\"produto\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @Test
    void retorna409ParaUuidDuplicado() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new PedidoDuplicadoException(id)).when(pedidoService).aceitar(any(Pedido.class));

        mockMvc.perform(post("/api/pedidos")
                        .contentType("application/json")
                        .content(jsonValido(id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_DUPLICADO"));
    }

    @Test
    void retorna503EnquantoPublicacaoConfirmadaNaoEstaDisponivel() throws Exception {
        UUID id = UUID.randomUUID();
        PedidoService serviceSemPublisherReal = new PedidoService(
                new PedidoEmMemoriaRepository(),
                pedido -> {
                    throw new PublicacaoPedidoException("Publicação indisponível", false);
                }
        );
        MockMvc mockMvcSemPublisherReal = mockMvcPara(serviceSemPublisherReal);

        mockMvcSemPublisherReal.perform(post("/api/pedidos")
                        .contentType("application/json")
                        .content(jsonValido(id)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PUBLICACAO_INDISPONIVEL"));
    }

    @Test
    void retornaStatusEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        Pedido pedido = new Pedido(id, "Teclado", 2, LocalDateTime.of(2026, 10, 12, 10, 30));
        when(pedidoService.consultar(id)).thenReturn(RegistroPedido.recebido(pedido));

        mockMvc.perform(get("/api/pedidos/status/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPedido").value(id.toString()))
                .andExpect(jsonPath("$.status").value(SituacaoPedido.RECEBIDO.name()))
                .andExpect(jsonPath("$.dataProcessamento").doesNotExist())
                .andExpect(jsonPath("$.mensagemErro").doesNotExist());
    }

    @Test
    void retorna404ParaPedidoDesconhecido() throws Exception {
        UUID id = UUID.randomUUID();
        when(pedidoService.consultar(id)).thenThrow(new PedidoNaoEncontradoException(id));

        mockMvc.perform(get("/api/pedidos/status/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_NAO_ENCONTRADO"));
    }

    @Test
    void retorna400ParaUuidMalformado() throws Exception {
        mockMvc.perform(get("/api/pedidos/status/uuid-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("UUID_INVALIDO"));
    }

    private String jsonValido(UUID id) {
        return """
                {
                  "id":"%s",
                  "produto":"Teclado",
                  "quantidade":2,
                  "dataCriacao":"2026-10-12T10:30:00"
                }
                """.formatted(id);
    }
}
