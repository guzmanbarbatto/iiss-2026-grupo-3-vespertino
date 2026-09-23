#!/bin/bash

# Configuración del entorno
BASE_URL="http://localhost:8080/api/v1"
TOKEN="token_secreto_desarrollo_123" # Este valor debe coincidir con el que Sebas configure en INGSOF-26
HEADERS=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

echo "================================================="
echo "Iniciando Validación E2E - API EcoWarm (INGSOF-32)"
echo "================================================="

# 1. Crear Habitación (POST)
echo -n "1. POST   /habitaciones (Creando registro)... "
PAYLOAD='{"nombre": "Sala de Servidores", "temperaturaEsperada": 20.0, "idTermostato": "TERM_01", "idSwitch": "SW_01"}'
# El flag -w "%{http_code}" extrae solo el status, y -o /dev/null oculta el JSON de respuesta.
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/habitaciones" "${HEADERS[@]}" -d "$PAYLOAD")
echo "HTTP $HTTP_CODE"

# Asumimos el ID 1 para continuar el flujo transaccional. Si la BD no se reinicia, este ID podría variar.
HAB_ID=1
SWITCH_ID="SW_01"

# 2. Listar Habitaciones (GET)
echo -n "2. GET    /habitaciones (Listando sitio)... "
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "${BASE_URL}/habitaciones" "${HEADERS[@]}")
echo "HTTP $HTTP_CODE"

# 3. Modificar Habitación (PATCH)
echo -n "3. PATCH  /habitaciones/$HAB_ID (Modificando temp)... "
PATCH_PAYLOAD='{"temperaturaEsperada": 18.5}'
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "${BASE_URL}/habitaciones/$HAB_ID" "${HEADERS[@]}" -d "$PATCH_PAYLOAD")
echo "HTTP $HTTP_CODE"

# 4. Accionar Switch (POST - Task)
echo -n "4. POST   /switches/$SWITCH_ID/acciones (Accionando ON)... "
ACTION_PAYLOAD='{"accion": "ON"}'
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/switches/$SWITCH_ID/acciones" "${HEADERS[@]}" -d "$ACTION_PAYLOAD")
echo "HTTP $HTTP_CODE"

# 5. Eliminar Habitación (DELETE)
echo -n "5. DELETE /habitaciones/$HAB_ID (Limpiando BD)... "
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${BASE_URL}/habitaciones/$HAB_ID" "${HEADERS[@]}")
echo "HTTP $HTTP_CODE"

echo "================================================="
echo "Ejecución finalizada."