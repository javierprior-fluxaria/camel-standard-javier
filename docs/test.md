### Configuración general en Postman

- **Método**: `POST`
- **URL**: `http://localhost:8080/api/v1/orders`
- **Headers**:
  - `Content-Type`: `application/json`
  - *(Opcional)* `X-Correlation-ID`: `test-postman-123` *(si no lo indicas, el middleware generará un UUID automáticamente y lo propagará en logs, base de datos y Kafka)*.

---

### 1. Escenario Feliz: Pedido Válido en EUR (Happy Path)
* **Objetivo:** Flujo completo: enriquecimiento de país vía `RestCountries` (España), persistencia en PostgreSQL (`ENRICHED`), envío al ERP (`CUST-OK`), actualización en PostgreSQL (`CONFIRMED`) y publicación en Kafka `orders.processed`.
* **Respuesta esperada:** `201 Created`
* **JSON Body:**
```json
{
  "orderId": "ORD-OK-001",
  "customerId": "CUST-OK",
  "country": "ES",
  "currency": "EUR",
  "items": [
    {
      "productId": "SHIRT-RED",
      "quantity": 2,
      "unitPrice": 29.95
    },
    {
      "productId": "JEANS-BLUE",
      "quantity": 1,
      "unitPrice": 59.95
    }
  ]
}
```
*(Nota: si repites la petición con el mismo `orderId`, el sistema detectará duplicidad y responderá 409; cambia el `orderId` a `ORD-OK-002` para nuevas pruebas).*

---

### 2. Conversión de Divisa Internacional (USD a EUR)
* **Objetivo:** Probar el enriquecimiento de país (`US`) y la llamada en vivo a la API de tipo de cambio para calcular `totalEur` con tasa aplicada.
* **Respuesta esperada:** `201 Created` (verás en la respuesta `totalEur` calculado y la tasa `exchangeRate`).
* **JSON Body:**
```json
{
  "orderId": "ORD-USD-001",
  "customerId": "CUST-OK",
  "country": "US",
  "currency": "USD",
  "items": [
    {
      "productId": "HEADPHONES-BT",
      "quantity": 1,
      "unitPrice": 100.00
    }
  ]
}
```

---

### 3. Validación de Negocio Fallida (Business Validator)
* **Objetivo:** Demostrar que pedidos con errores funcionales (país no existente `XX`, divisa inventada `INVALID`, cantidades negativas o precios 0) son rechazados inmediatamente en la capa de aplicación y **nunca tocan ni la base de datos ni los sistemas externos**.
* **Respuesta esperada:** `400 Bad Request` con esquema estándar RFC 7807 (`application/problem+json`) y array `invalidParams`.
* **JSON Body:**
```json
{
  "orderId": "ORD-BAD-001",
  "customerId": "CUST-OK",
  "country": "XX",
  "currency": "INVALID",
  "items": [
    {
      "productId": "SHIRT-RED",
      "quantity": -5,
      "unitPrice": 0.00
    }
  ]
}
```

---

### 4. Idempotencia y Conflicto Duplicado (ERP o Base de Datos)
* **Objetivo:** El cliente `CUST-DUP` hace que el ERP detecte un pedido ya registrado. Además, si se reintenta un pedido con el mismo `orderId`, PostgreSQL lanzará un conflicto de clave primaria. En ambos casos el middleware responde de forma estandarizada.
* **Respuesta esperada:** `409 Conflict` (formato RFC 7807).
* **JSON Body:**
```json
{
  "orderId": "ORD-DUP-001",
  "customerId": "CUST-DUP",
  "country": "FR",
  "currency": "EUR",
  "items": [
    {
      "productId": "JACKET-BLACK",
      "quantity": 1,
      "unitPrice": 120.00
    }
  ]
}
```

---

### 5. ERP Lento (Timeout de Red)
* **Objetivo:** El cliente `CUST-SLOW` hace que el ERP tarde 5 segundos en responder. Como el cliente HTTP tiene un timeout de lectura configurado a 3 segundos (`read-timeout-ms: 3000`), la llamada corta por timeout y devuelve un error controlado.
* **Respuesta esperada:** `500 Internal Server Error` (RFC 7807 indicando timeout de lectura).
* **JSON Body:**
```json
{
  "orderId": "ORD-SLOW-001",
  "customerId": "CUST-SLOW",
  "country": "DE",
  "currency": "EUR",
  "items": [
    {
      "productId": "COAT-WINTER",
      "quantity": 1,
      "unitPrice": 199.00
    }
  ]
}
```

---

### 6. ERP 500 Intermitente (Política de Reintentos con Backoff)
* **Objetivo:** El cliente `CUST-ERR500` simula fallos temporales en el ERP. Falla dos veces con 500 y al tercer intento responde OK. Verás en los logs de la aplicación los avisos de reintento (`Reintentando llamada a Legacy ERP tras fallo HTTP 500...`) y finalmente la respuesta exitosa.
* **Respuesta esperada:** `201 Created` tras recuperarse en los reintentos.
* **JSON Body:**
```json
{
  "orderId": "ORD-RETRY-001",
  "customerId": "CUST-ERR500",
  "country": "IT",
  "currency": "EUR",
  "items": [
    {
      "productId": "SHOES-BROWN",
      "quantity": 1,
      "unitPrice": 85.00
    }
  ]
}
```

---

### 7. ERP 503 Permanente (Circuit Breaker Resilience4j)
* **Objetivo:** El cliente `CUST-ERR503` simula que el ERP está caído o en mantenimiento (503 constante). El Circuit Breaker interviene para proteger el flujo.
* **Respuesta esperada:** `500 Internal Server Error` controlado.
* **JSON Body:**
```json
{
  "orderId": "ORD-CIRCUIT-001",
  "customerId": "CUST-ERR503",
  "country": "PT",
  "currency": "EUR",
  "items": [
    {
      "productId": "BELT-LEATHER",
      "quantity": 1,
      "unitPrice": 35.00
    }
  ]
}
```

---

## B · FLUJO ASÍNCRONO: LOTE DE PEDIDOS (KAFKA)

### Publicación y Verificación de Lotes

El flujo B se activa enviando un array JSON de pedidos al topic `orders.batch.in`.
Puedes probarlo automáticamente con:
```powershell
.\test-batch.ps1
```

O manualmente publicando el lote en Kafka:
```bash
docker exec -i fluxaria-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders.batch.in
```

#### Payload del Lote de Ejemplo (`orders.batch.in`):
```json
[
  {
    "orderId": "BATCH-ORD-01",
    "customerId": "CUST-OK",
    "country": "ES",
    "currency": "EUR",
    "items": [
      { "productId": "SHIRT-RED", "quantity": 1, "unitPrice": 40.00 }
    ]
  },
  {
    "orderId": "BATCH-ORD-02",
    "customerId": "CUST-OK",
    "country": "XX",
    "currency": "INVALID",
    "items": [
      { "productId": "BAD-ITEM", "quantity": -2, "unitPrice": 0.00 }
    ]
  },
  {
    "orderId": "BATCH-ORD-03",
    "customerId": "CUST-OK",
    "country": "FR",
    "currency": "EUR",
    "items": [
      { "productId": "JEANS-BLUE", "quantity": 2, "unitPrice": 50.00 }
    ]
  },
  {
    "orderId": "BATCH-ORD-04",
    "customerId": "CUST-DUP",
    "country": "DE",
    "currency": "EUR",
    "items": [
      { "productId": "JACKET-BLACK", "quantity": 1, "unitPrice": 90.00 }
    ]
  }
]
```

#### Resumen Esperado en `orders.batch.summary`:
```json
{
  "batchId": "BATCH-XXXX",
  "totalOrders": 4,
  "processedCount": 2,
  "failedCount": 2,
  "totalEur": 140.00,
  "processedAt": "2026-10-05T...",
  "correlationId": "..."
}
```

#### Mensajes Esperados en `orders.dlq`:
Los 2 pedidos fallidos (`BATCH-ORD-02` por validación y `BATCH-ORD-04` por conflicto de idempotencia ERP) se desvían a `orders.dlq` con cabeceras `X-Failure-Reason`, `X-Failed-At` y el payload original intacto.