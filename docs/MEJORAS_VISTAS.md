# Mejoras de las vistas de Voxora

1 de noviembre de 2026. Referencia visual: las siete capturas de Dzone aportadas por el usuario, conservadas solo en `docs/referencias-locales/dzone`.

Se actualizó la interfaz JavaFX con navegación activa, ubicación de la vista en la barra superior, jerarquía visual y colores diferenciados por prioridad y estado. Inicio ofrece contadores enlazados a filtros y una tabla de trabajo abierto por prioridad. Mi jornada muestra tarjetas de incidencias asignadas o en proceso dentro del alcance autorizado del usuario.

El listado incorpora filtros combinables de texto, estado, prioridad y responsable (este último para supervisores), contador de resultados y apertura por doble clic, Enter o botón. Las tablas adaptan su altura al número de registros y las filas muestran información adicional al pasar el cursor. La revisión mantiene el filtro En revisión y explica cuándo no hay reportes pendientes.

El Kanban utiliza cinco columnas con contadores, tarjetas independientes y acciones de apertura. Indicadores muestra conteos, distribución por prioridad y mediana entre inicio y cierre para registros completados con fechas válidas; incluye la espera de revisión y no representa tiempo efectivo. No se inventa una reincidencia correctiva sin catálogo de tipos.

Las pruebas JavaFX usan datos temporales para comprobar filtros, navegación, tarjetas y apertura de formularios; las imágenes de verificación quedan en `target/ui-evidence`. No se cargan ejemplos en la base personal. Se conservan los servicios de autorización, estados y flujo de voz existentes.

Hallazgos como flujo propio, asignación automática y preventivos programados siguen pendientes. No se añadieron botones sin implementación ni se reutilizaron los registros de la demo. La recuperación de la contraseña de admin es una tarea independiente que continúa pendiente; esta mejora no cambia credenciales.

Cambios locales: no se publican automáticamente ni se actualizan los manuales Word históricos durante esta entrega de interfaz.
