# Laboratorio 14: Config Server y Resiliencia


## Objetivo

Implementar configuración centralizada con **Spring Cloud Config Server** y tolerancia a fallos con **Resilience4j** (Circuit Breaker, Retry y Bulkhead) en una arquitectura de microservicios.

## Tecnologías

- Java 21
- Spring Boot 3.5.3
- Spring Cloud 2025.0.0
- Spring Cloud Config (Server y Client)
- Spring Cloud Netflix Eureka
- Spring Cloud OpenFeign
- Resilience4j (Circuit Breaker, Retry, Bulkhead) con Actuator y AOP
- Spring Data JPA y MySQL
- Lombok y Maven

## Arquitectura

| Módulo | Puerto (dev) | Puerto (prod) | Función |
|---|---|---|---|
| `eureka-server` | 8761 | - | Registro de servicios |
| `config-server` | 8888 | - | Configuración centralizada |
| `cliente-service` | 8081 | 9091 | Gestiona clientes |
| `categoria-service` | 8082 | 9092 | Gestiona categorías |
| `producto-service` | 8083 | 9093 | Gestiona productos (consume categoria-service) |
| `pedido-service` | 8084 | 9094 | Gestiona pedidos (consume cliente-service y producto-service) |

```
pedido-service ──► cliente-service
       │
       └────────► producto-service ──► categoria-service

Todos los servicios leen su configuración desde config-server,
que a su vez lee el repositorio config-repo en GitHub.
```

## Repositorio de configuración

La configuración vive en un repositorio aparte:
https://github.com/AlexanderFtn06/config-repo

Por cada servicio hay tres archivos:

- `<servicio>.yml`: propiedades comunes (nombre, JPA, mensaje).
- `<servicio>-dev.yml`: puerto y base de datos de desarrollo.
- `<servicio>-prod.yml`: puerto y base de datos de producción.

El perfil activo de cada servicio es `dev`, definido en su `application.yml` junto con:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

## Patrones de resiliencia

| Servicio consumidor | Servicio consumido | Patrones |
|---|---|---|
| `producto-service` | `categoria-service` | Circuit Breaker, Retry y Bulkhead |
| `pedido-service` | `cliente-service` | Circuit Breaker y Retry (con timeout de Feign de 3 s) |
| `pedido-service` | `producto-service` | Circuit Breaker y Bulkhead |

Cada consumidor tiene una clase `*CircuitService` (`CategoriaCircuitService`, `ClienteCircuitService`, `ProductoCircuitService`) con las anotaciones de Resilience4j y un método **fallback** que devuelve datos temporales cuando el servicio consumido no responde.

Configuración principal de `pedido-service`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      clienteService / productoService:
        slidingWindowSize: 5
        minimumNumberOfCalls: 3
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
  retry:
    instances:
      clienteRetry:
        maxAttempts: 3
        waitDuration: 2s
  bulkhead:
    instances:
      productoBulkhead:
        maxConcurrentCalls: 2
        maxWaitDuration: 1s
```

## Cómo ejecutar

Requisitos: JDK 17 o superior, Maven y MySQL en el puerto 3306 con las bases `bd_categoria_dev`, `bd_producto_dev`, `bd_cliente_dev` y `bd_pedido_dev`.

Iniciar en este orden y esperar a que cada uno termine de arrancar:

1. `config-server`
2. `eureka-server`
3. `categoria-service`
4. `cliente-service`
5. `producto-service`
6. `pedido-service`

## Pruebas

**Config Server**

- http://localhost:8888/categoria-service/default
- http://localhost:8888/producto-service/dev
- http://localhost:8888/cliente-service/prod

**Circuit Breaker**

1. Con todo activo: http://localhost:8083/api/productos devuelve la categoría real.
2. Detener `categoria-service`: la respuesta trae `"Categoría temporal no disponible"` y la consola muestra `Circuit Breaker activado`.
3. En `pedido-service`, `GET /api/pedidos/1` devuelve cliente y producto temporales al detener `cliente-service` o `producto-service`.

**Retry**

- Con el servicio consumido caído o lento (por encima del timeout), la consola repite `Llamando a ...` según `maxAttempts` y termina con `Fallback ejecutado`.

**Bulkhead**

- Con el servicio consumido lento (`Thread.sleep` de 10 s) y varias pestañas abiertas a la vez, solo 2 llamadas entran; las demás reciben el fallback con el mensaje `Bulkhead ... is full and does not permit further calls`.

## Retry vs Bulkhead

| | Retry | Bulkhead |
|---|---|---|
| Qué hace | Reintenta una llamada que falló | Limita las llamadas concurrentes a un servicio |
| Cuándo usarlo | Fallos pasajeros (red, timeouts breves) | Servicios lentos que podrían agotar los hilos del consumidor |
| Riesgo | Añade carga y demora si el servicio sigue caído | Rechaza peticiones si el límite es muy bajo |

## Notas

- Si el `@CircuitBreaker` tiene `fallbackMethod`, atrapa el error antes que el `@Retry` y este no reintenta. Por eso en `ClienteCircuitService` el Circuit Breaker no define fallback.
- Los `Thread.sleep` usados para simular lentitud se retiraron del código entregado.