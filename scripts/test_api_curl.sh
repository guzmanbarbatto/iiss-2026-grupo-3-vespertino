#!/bin/bash

# Configuración del entorno
BASE_URL="http://localhost:8080/api/v1"

# 1. ELIMINAMOS LA SEGURIDAD: Como borramos la carpeta 'security', ya no necesitamos el Token.
# Solo dejamos el Content-Type para que Spring sepa que le mandamos JSON.
HEADERS=(-H "Content-Type: application/json")

echo "================================================="
echo "Iniciando Validación E2E - API EcoWarm (Iteración 4)"
echo "================================================="

# 2. ACTUALIZAMOS EL PAYLOAD: Agregamos el atributo "consumo" que la cátedra exigió para esta iteración.
echo -n "1. POST   /habitaciones (Creando registro)... "
PAYLOAD='{"nombre": "Sala de Servidores", "temperaturaEsperada": 20.0, "idTermostato": "TERM_01", "idSwitch": "SW_01", "consumo": 1500.0}'
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/habitaciones" "${HEADERS[@]}" -d "$PAYLOAD")
echo "HTTP $HTTP_CODE"

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