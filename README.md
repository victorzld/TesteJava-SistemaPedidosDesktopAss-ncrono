# Sistema de Pedidos Desktop Assíncrono

Aplicação Java 21 composta por uma interface **Java Swing** e uma API **Spring Boot 4.1.1**. O desktop envia pedidos por HTTP; o backend os processa de forma assíncrona com **RabbitMQ**, publica eventos de sucesso ou falha e encaminha pedidos rejeitados para uma **Dead Letter Queue (DLQ)**.

Os status são mantidos apenas em memória, sem banco de dados. O processamento dura de 1 a 3 segundos e possui 20% de probabilidade de falha simulada.

## Pré-requisitos

- **JDK 21** (`java -version` e `./mvnw -version` devem indicar Java 21).
- **Docker Engine** e **Docker Compose** (plugin v2).
- Ambiente gráfico para abrir o Java Swing (Linux, Windows ou macOS).
- Acesso à internet na primeira execução para baixar dependências Maven e a imagem do RabbitMQ.

O Maven não precisa ser instalado globalmente: o projeto inclui o **Maven Wrapper 3.9.9**.

## Executar localmente

Clone o projeto e entre na pasta raiz:

```bash
git clone https://github.com/victorzld/TesteJava-SistemaPedidosDesktopAss-ncrono.git
cd TesteJava-SistemaPedidosDesktopAss-ncrono
```

**1. Inicie o RabbitMQ:**

```bash
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

Aguarde o container ficar `healthy`. Painel: [http://127.0.0.1:15672](http://127.0.0.1:15672) (usuário `pedidos`, senha `pedidos_local_123` — somente para desenvolvimento local).

**2. Compile e execute os testes:**

```bash
./mvnw clean verify
```

**3. Inicie o backend** em um terminal e mantenha-o aberto:

```bash
./mvnw -pl backend spring-boot:run
```

**4. Inicie o desktop** em outro terminal, também na raiz:

```bash
./mvnw -pl desktop exec:java
```

No Swing, informe **produto** e **quantidade**, clique em **Enviar Pedido** e acompanhe os status na tabela. A atualização ocorre automaticamente a cada 3 segundos.

> No Windows, substitua `./mvnw` por `mvnw.cmd`. No Linux, se necessário, execute `chmod +x mvnw` antes do primeiro build.

## Acessos e configurações

| Serviço             | Endereço                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------------ |
| API REST            | `http://127.0.0.1:9090`                                                                    |
| Swagger UI          | [http://127.0.0.1:9090/swagger-ui/index.html](http://127.0.0.1:9090/swagger-ui/index.html) |
| RabbitMQ Management | [http://127.0.0.1:15672](http://127.0.0.1:15672)                                           |

Endpoints: `POST /api/pedidos` (aceite assíncrono) e `GET /api/pedidos/status/{id}` (consulta). O contrato OpenAPI está em [`api/openapi.yaml`](api/openapi.yaml).

Se alterar a porta da API, ajuste **ambos** os arquivos: `backend/src/main/resources/application.properties` (`server.port`) e `desktop/src/main/resources/desktop.properties` (`api.base-url`).

## Testes e observações

O comando `./mvnw clean verify` executa os testes automatizados de **backend** e **desktop**, incluindo o teste unitário obrigatório da publicação RabbitMQ. Os testes unitários são isolados do broker; para conferir o fluxo real, utilize o Swing e as filas no painel RabbitMQ.

Os status em memória são perdidos quando o backend reinicia. As filas do RabbitMQ são duráveis, portanto pedidos pendentes podem permanecer no broker; este é um projeto demonstrativo e não implementa recuperação persistente do estado dos pedidos após reinício.

Para encerrar o RabbitMQ:

```bash
docker compose -f infra/docker-compose.yml down
```
