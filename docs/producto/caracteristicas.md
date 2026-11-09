# Características Principales de EcoWarm

Listado de las funcionalidades clave que diferencian al producto en el mercado de domótica. Es la **visión del producto**: el estado de cada una al cierre de la Iteración 6 va debajo, y lo que falta está en el [roadmap](../arquitectura/roadmap.md).

- **Gestión Inteligente Tarifaria:** Optimización automática del encendido y apagado de la calefacción, sincronizada con los horarios, planes y costos del plan multihorario de UTE para maximizar el ahorro económico.
  - *Estado: parcial.* El controlador no enciende nada en la tarifa punta configurada en el sitio (franja y días) y reparte la potencia contratada sin superarla. No conoce los planes ni los costos de UTE, ni la tarifa valle.
- **Integración de Pronóstico Meteorológico:** Capacidad del sistema para ajustar proactivamente los objetivos de temperatura de cada habitación tomando en consideración el clima exterior proyectado para las próximas 48 horas.
  - *Estado: no implementada.*
- **Interfaz Conversacional (GenAI):** Integración nativa con un modelo de lenguaje grande (LLM) que permite al usuario interactuar con el sistema mediante lenguaje natural para consultar el estado del hogar o solicitar consejos de eficiencia (ej. "¿Cuál fue mi consumo esta semana y cómo puedo mejorarlo?").
  - *Estado: parcial.* El servidor MCP (`modules/mcp`) le da a un agente de IA 7 tools: consultar el sitio, el estado de las habitaciones y el histórico de temperaturas, e iniciar, detener o accionar un switch ([pruebas](../mcp/pruebas-mcp.md)). No hay datos de consumo para dar consejos de eficiencia.
- **Simulador de Consumo Energético:** Herramienta predictiva que proyecta las temperaturas alcanzables y los costos estimados en la factura de luz, basándose en diferentes configuraciones de habitaciones encendidas y la potencia máxima contratada por el usuario.
  - *Estado: no implementada.*
- **Perfilamiento Térmico Histórico:** Registro continuo y evaluación analítica del tiempo exacto que le toma a cada habitación particular calentarse y enfriarse (inercia térmica), lo que permite refinar y mejorar la precisión de las predicciones futuras del termostato.
  - *Estado: no implementada.* El histórico de temperaturas se guarda, pero no se analiza. El engine mide cada cuánto publica cada termostato, pero lo usa para detectar un termostato caído, no para estimar la inercia térmica.
