package br.com.pedidos.backend;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"spring.rabbitmq.dynamic=false"
})
class BackendApplicationTests {

	@Autowired
	private RabbitListenerEndpointRegistry listenerRegistry;

	@Test
	void contextLoadsWithoutStartingRabbitConsumers() {
		assertThat(listenerRegistry.getListenerContainers())
				.isNotEmpty()
				.allMatch(container -> !container.isRunning());
	}

}
