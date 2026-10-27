curl -X POST http://localhost:8080/control/comandos \
-H "Content-Type: application/json" \
-H "X-API-Key: clave-secreta-grupo-3" \
-d '{
  "accion": "INICIAR",
  "fechaHora": "2026-10-26T16:30:00-03:00",
  "factorTiempo": 60.0
}'