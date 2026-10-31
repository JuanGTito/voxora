# Validación local de voz de Voxora 0.2.0

Fecha de trabajo: 31 de octubre de 2026. Motores CPU: whisper.cpp b5454 con Whisper Tiny multilingüe y llama.cpp b11838 con Qwen3 4B Q4_K_M. Versiones, tamaños, procedencia y SHA-256 están fijados en `src/main/resources/ai/components.json`.

Se utiliza audio sintético español creado con Microsoft Sabina Desktop; no se graba el micrófono. Los datos y cuentas de prueba están aislados de cualquier instalación personal. El equipo tiene aproximadamente 32 GB de RAM. Esta comprobación no acredita el objetivo de 4 GB ni calidad general frente a ruido o acentos.

El recorrido probado importa WAV, convierte a PCM, transcribe realmente, conserva el original, introduce una corrección de prueba que simula la revisión del técnico, organiza con Qwen3 real, aplica como borrador, envía, aprueba con otra identidad y produce PDF. La IA nunca realiza las dos decisiones humanas de envío y aprobación.

En la muestra corta se observaron alrededor de 3,5 segundos de transcripción y 12 segundos de extracción, incluidos el arranque y la parada del motor de lenguaje. Son observaciones de una muestra local; no constituyen umbrales garantizados ni benchmarks representativos. La medición exacta de la última ejecución está en `target/ai-evidence/local-models.json`.

Whisper produjo «No se medio presión» en lugar de «No se midió presión». La prueba conserva ese original y proporciona el texto corregido antes de extraer; no se presenta la corrección como salida automática del transcriptor. La propuesta revisada conservó la negación, separó la medición no realizada como observación y mantuvo la junta mencionada como material.

Se detectó un formato de micrófono compatible mediante Java Sound, sin abrirlo ni grabarlo. La prueba de voz humana debe realizarse con la acción explícita Grabar micrófono. También quedan pendientes el ensayo de tres minutos, la VM de 4 GB, Windows 10/11 en otro equipo y medición de memoria conjunta de sistema, aplicación y procesos nativos.

La suite ordinaria se ejecuta sin modelos; la prueba de motores reales se habilita expresamente con `scripts/test-ai.ps1`. La prueba de interfaz verifica que aplicar la propuesta conserva el estado En proceso y guarda una versión, sin enviar ni aprobar automáticamente.
