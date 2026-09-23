#!/bin/bash

API_URL="http://localhost:8080/api"
TOKEN="secret-token-123"

echo "=== 1. Probar acceso denegado sin Token ==="
curl -i -X GET "${API_URL}/rooms"

echo -e "\n\n=== 2. Listar Habitaciones (con Bearer Token) ==="
curl -i -X GET "${API_URL}/rooms" \
  -H "Authorization: Bearer ${TOKEN}"

echo -e "\n\n=== 3. Modificación Parcial (PATCH) de la Habitación 0 ==="
curl -i -X PATCH "${API_URL}/rooms/0" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"temperaturaEsperada": 25.0}'

echo -e "\n\n=== 4. Accionar Switch ON manual (Comando) ==="
curl -i -X POST "${API_URL}/rooms/0/switch" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"action": "ON"}'

echo -e "\n\n=== 5. Iniciar Controlador (Comando) ==="
curl -i -X POST "${API_URL}/controller/start" \
  -H "Authorization: Bearer ${TOKEN}"

echo -e "\n\n=== 6. Validar Consistencia (Tarea) ==="
curl -i -X POST "${API_URL}/tasks/validate-consistency" \
  -H "Authorization: Bearer ${TOKEN}"