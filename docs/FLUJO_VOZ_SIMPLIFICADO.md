# Hablar detener y revisar

Actualización local del 1 de noviembre de 2026. El mismo flujo guiado se usa al crear una incidencia y al documentar la atención del técnico.

1. Pulsar **Hablar** en el formulario: abre la ventana e inicia la captura como resultado de esa acción explícita. El icono de micrófono, el pulso animado y el tiempo indican que está grabando. La animación representa el estado de captura; no es un medidor de volumen.
2. Pulsar **Detener**: se conserva el audio y se ejecutan automáticamente Whisper Tiny y Qwen3 4B, con estados visibles de transcripción y organización. No hace falta pulsar dos botones de procesamiento. El límite de tres minutos detiene la captura y continúa con la misma preparación.
3. Leer el resultado y editarlo directamente. **Guardar borrador** guarda el reporte de atención sin enviarlo; **Usar descripción** completa el formulario de incidencia, que aún requiere confirmar sus campos y pulsar Crear incidencia.

La narración original se conserva. El desplegable **Escribir o corregir la narración** permite corregir el reconocimiento, actualizar el resultado o importar un WAV. La importación también encadena la preparación automáticamente. Cambiar la fuente invalida la propuesta anterior hasta actualizarla. Los errores muestran una opción para reintentar y conservan los datos persistidos; cerrar cancela el trabajo pendiente y detiene el micrófono.

Las narraciones anteriores están disponibles en **Recuperar una narración anterior**. Para incidencias todavía no creadas, el audio y la narración se guardan por usuario en `pending-dictation/u-<id>` dentro de la carpeta de datos. No crean tickets ni pertenecen aún a su historial; no están incluidos en el respaldo de incidencias. Los audios asociados a atenciones existentes mantienen su almacenamiento y respaldo habituales.

Las correcciones del resultado final se guardan al confirmar el botón de guardado, no por el solo hecho de escribirlas. La confirmación de reemplazo protege el contenido que ya exista en el formulario. En el reporte se conservan la propuesta de IA, la fuente y la versión humana enlazada; el supervisor sigue aprobando por separado.

No se infieren automáticamente equipo, responsable, prioridad ni especialidad. El formulario de nueva incidencia conserva sus validaciones y el técnico solo puede preparar reportes de su trabajo asignado En proceso. Al organizar una descripción nueva se añaden las oraciones que el modelo no clasificó, evitando omitir detalles de la narración.

La prueba automática no graba el entorno: utiliza audio sintético para los motores reales y un adaptador controlado para probar el encadenamiento, los fallos y la edición en JavaFX. La validación con voz humana y el objetivo de 4 GB siguen pendientes. Los manuales Word anteriores documentan la versión previa del flujo.

Validación de esta entrega: 50 pruebas aprobadas sin fallos ni omisiones mediante `scripts/test-ai.ps1`: nueve de interfaz y preparación de nueva incidencia (incluida una con motores reales), doce de voz y procedencia, 28 del circuito de negocio y una del recorrido real de audio a reporte aprobado/PDF. Las pruebas de interfaz verifican explícitamente el encadenamiento sin clics intermedios y la conservación de la transcripción si falla la organización.
