package br.com.pedidos.backend.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false"
})
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void generatedOpenApiContainsOrderOperationsAndResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.paths['/api/pedidos'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos/status/{id}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos/status/{id}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos/status/{id}'].get.responses['404']").exists());
    }
}
