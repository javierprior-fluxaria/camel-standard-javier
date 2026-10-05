$baseUrl = "http://localhost:8080/api/v1/orders"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "INICIANDO BATERIA DE PRUEBAS END-TO-END: PEDIDO INTERNACIONAL" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

function Assert-Scenario {
    param(
        [string]$ScenarioName,
        [string]$Payload,
        [int]$ExpectedStatus
    )

    Write-Host "`n--> Ejecutando: $ScenarioName" -ForegroundColor Yellow
    try {
        $response = Invoke-WebRequest -Uri $baseUrl -Method Post -Body $Payload -ContentType "application/json" -UseBasicParsing
        $statusCode = [int]$response.StatusCode
        
        if ($statusCode -eq $ExpectedStatus) {
            Write-Host "[PASS] Status recibido: $statusCode (Esperado: $ExpectedStatus)" -ForegroundColor Green
            Write-Host "Respuesta: $($response.Content)" -ForegroundColor Gray
        } else {
            Write-Host "[FAIL] Status recibido: $statusCode pero se esperaba: $ExpectedStatus" -ForegroundColor Red
            Write-Host "Respuesta: $($response.Content)" -ForegroundColor Red
        }
    } catch {
        if ($_.Exception.Response) {
            $actualStatus = [int]$_.Exception.Response.StatusCode
            $stream = $_.Exception.Response.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                $errorBody = $reader.ReadToEnd()
            } else {
                $errorBody = $_.ErrorDetails.Message
            }
            
            if ($actualStatus -eq $ExpectedStatus) {
                Write-Host "[PASS] Status recibido: $actualStatus (Esperado: $ExpectedStatus)" -ForegroundColor Green
                Write-Host "Cuerpo Error RFC 7807: $errorBody" -ForegroundColor Gray
            } else {
                Write-Host "[FAIL] Status recibido: $actualStatus pero se esperaba: $ExpectedStatus" -ForegroundColor Red
                Write-Host "Detalle: $errorBody" -ForegroundColor Red
            }
        } else {
            Write-Host "[FAIL] Error de conexion o script: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

$suffix = [System.DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

# --- TEST 1: Pedido Valido (ES, EUR) -> 201 Created
$p1 = '{"orderId":"ORD-OK-' + $suffix + '","customerId":"CUST-OK","country":"ES","currency":"EUR","items":[{"productId":"SHIRT-RED","quantity":2,"unitPrice":29.95},{"productId":"JEANS-BLUE","quantity":1,"unitPrice":59.95}]}'
Assert-Scenario -ScenarioName "Test 1: Pedido Valido (Exito 201 Created)" -Payload $p1 -ExpectedStatus 201

# --- TEST 2: Validacion de Negocio (Moneda y Pais invalidos) -> 400 Bad Request
$p2 = '{"orderId":"ORD-BAD-' + $suffix + '","customerId":"CUST-OK","country":"XX","currency":"INVALID","items":[{"productId":"SHIRT-RED","quantity":-5,"unitPrice":0.00}]}'
Assert-Scenario -ScenarioName "Test 2: Validacion de Negocio Fallida (400 Bad Request)" -Payload $p2 -ExpectedStatus 400

# --- TEST 3: Idempotencia / Conflicto -> 409 Conflict
$p3 = '{"orderId":"ORD-DUP-' + $suffix + '","customerId":"CUST-DUP","country":"FR","currency":"EUR","items":[{"productId":"JACKET-BLACK","quantity":1,"unitPrice":120.00}]}'
Assert-Scenario -ScenarioName "Test 3: Idempotencia ERP / Conflicto Duplicado (409 Conflict)" -Payload $p3 -ExpectedStatus 409

# --- TEST 4: ERP Delay 5s (Supera timeout de 3s) -> Error controlado 500
$p4 = '{"orderId":"ORD-SLOW-' + $suffix + '","customerId":"CUST-SLOW","country":"DE","currency":"EUR","items":[{"productId":"COAT-WINTER","quantity":1,"unitPrice":199.00}]}'
Assert-Scenario -ScenarioName "Test 4: ERP Lento 5s (Timeout de Red -> 500)" -Payload $p4 -ExpectedStatus 500

# --- TEST 5: ERP 500 Intermitente (Reintentos con Backoff Exponencial y posterior 201 OK)
$p5 = '{"orderId":"ORD-RETRY-' + $suffix + '","customerId":"CUST-ERR500","country":"IT","currency":"EUR","items":[{"productId":"SHOES-BROWN","quantity":1,"unitPrice":85.00}]}'
Assert-Scenario -ScenarioName "Test 5: ERP 500 Intermitente (Exito tras reintentos -> 201 Created)" -Payload $p5 -ExpectedStatus 201

# --- TEST 6: ERP 503 Permanente (Apertura de Circuit Breaker) -> 500
$p6 = '{"orderId":"ORD-CIRCUIT-' + $suffix + '","customerId":"CUST-ERR503","country":"PT","currency":"EUR","items":[{"productId":"BELT-LEATHER","quantity":1,"unitPrice":35.00}]}'
Assert-Scenario -ScenarioName "Test 6: ERP 503 Permanente (Fallo controlado / Circuit Breaker)" -Payload $p6 -ExpectedStatus 500

Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host "BATERIA DE PRUEBAS COMPLETADA" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan