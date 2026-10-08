# ==============================================================================
# SCRIPT DE PRUEBAS END-TO-END: FLUJO B (PROCESAMIENTO ASINCRONO POR LOTES KAFKA)
# ==============================================================================

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "INICIANDO BATERIA DE PRUEBAS FLUJO B: LOTE DE PEDIDOS EN KAFKA" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

$suffix = [System.DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$batchId = "BATCH-RUN-$suffix"

# Lote mixto de 4 pedidos: 2 validos (ES, FR) y 2 fallidos (XX/INVALID y CUST-DUP)
$batchPayload = @"
[
  {
    "orderId": "BATCH-ORD-01-$suffix",
    "customerId": "CUST-OK",
    "country": "ES",
    "currency": "EUR",
    "items": [
      { "productId": "SHIRT-RED", "quantity": 1, "unitPrice": 40.00 }
    ]
  },
  {
    "orderId": "BATCH-ORD-02-$suffix",
    "customerId": "CUST-OK",
    "country": "XX",
    "currency": "INVALID",
    "items": [
      { "productId": "BAD-ITEM", "quantity": -2, "unitPrice": 0.00 }
    ]
  },
  {
    "orderId": "BATCH-ORD-03-$suffix",
    "customerId": "CUST-OK",
    "country": "FR",
    "currency": "EUR",
    "items": [
      { "productId": "JEANS-BLUE", "quantity": 2, "unitPrice": 50.00 }
    ]
  },
  {
    "orderId": "BATCH-ORD-04-$suffix",
    "customerId": "CUST-DUP",
    "country": "DE",
    "currency": "EUR",
    "items": [
      { "productId": "JACKET-BLACK", "quantity": 1, "unitPrice": 90.00 }
    ]
  }
]
"@

# Compactar a una sola linea JSON para kafka-console-producer sin que PowerShell lo envuelva en un objeto
$compactJson = ($batchPayload -replace "`r", "" -replace "`n", "").Trim()

Write-Host "`n--> Publicando lote de prueba en topic 'orders.batch.in' (BatchId: $batchId)..." -ForegroundColor Yellow
$compactJson | docker exec -i fluxaria-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders.batch.in

Write-Host "[OK] Lote publicado en Kafka orders.batch.in" -ForegroundColor Green
Write-Host "Esperando 5 segundos para que Camel procese el lote asincrono..." -ForegroundColor Gray
Start-Sleep -Seconds 5

Write-Host "`n--> Leyendo ultimo mensaje de topic 'orders.batch.summary'..." -ForegroundColor Yellow
$allSummaries = docker exec fluxaria-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.batch.summary --from-beginning --timeout-ms 8000
$summary = ($allSummaries | Where-Object { $_ -match '"batchId"' })[-1]
Write-Host "Resumen recibido: $summary" -ForegroundColor Cyan

Write-Host "`n--> Leyendo mensajes desviados a Dead Letter Queue 'orders.dlq'..." -ForegroundColor Yellow
$allDlq = docker exec fluxaria-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders.dlq --from-beginning --timeout-ms 8000
$dlq = ($allDlq | Where-Object { $_ -match '"orderId"' })
Write-Host "Mensajes en DLQ: $dlq" -ForegroundColor Gray

Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host "PRUEBA DEL FLUJO B COMPLETADA EXITOSAMENTE" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
