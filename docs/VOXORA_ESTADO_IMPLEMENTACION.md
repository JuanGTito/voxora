# Estado histórico del primer incremento Voxora 0.1.0

Actualización: 31 de octubre de 2026. Este documento complementa la consolidación inicial del 30 de octubre; sus observaciones sobre la plantilla describían el estado anterior a la implementación.

Este archivo conserva el estado anterior. La integración posterior de voz se documenta en [VALIDACION_VOZ.md](VALIDACION_VOZ.md) y [GUIA_EJECUCION.md](GUIA_EJECUCION.md).

## Resultado construido

Aplicación JavaFX de escritorio con SQLite local, sin voz ni LLM. Paquete raíz conservado: `dev.voxora`. No se tradujo la demo Python ni se importaron sus datos: se implementó el circuito de referencia con servicios de aplicación y un puerto de persistencia transaccional.

| Entrega del plan | Implementación disponible | Evidencia / límite |
|---|---|---|
| E0 | Fuentes y consolidación | Documentos en `docs/` |
| E1 | Primer superadmin, usuarios, roles, Argon2id, login/logout y sesiones | Pruebas de credenciales, desactivación, revocación y permisos; plantillas de permisos, sin editor granular |
| E2 | Crear, asignar/reasignar antes de iniciar, listar/buscar/filtrar, iniciar e historial | Pruebas de propiedad, especialidad exacta, estados y transacciones |
| E3 | Editor manual, autoguardado, recuperación de lo persistido, versiones, vista previa y envío | Pruebas de cancelación, versión antigua, doble envío y cierre del editor |
| E4 | Revisión, devolución motivada, aprobación separada y PDF | Pruebas de fallo de PDF, conservación de estado y regeneración de PDF faltante |
| E5 | Contadores, Kanban, DOCX, auditoría, inactividad, respaldo y restauración | Pruebas de tiempos con reloj controlado, roundtrip, ZIP inválido/corrupto y recuperación de restauración interrumpida |
| E6 | Maven Wrapper, scripts, CI declarada, app con runtime y MSI generados | Arranque local del EXE observado; MSI no instalado; CI remota y otro equipo/Windows pendientes |
| E7 | Aplazada según petición | No hay micrófono, transcriptor, modelo, motor ni resultado de IA simulado |
| Extensiones | Aplazadas | Hallazgos, preventivos y asignación automática |

No se declara cerrada la aceptación externa de E6 ni la totalidad de requisitos futuros del plan maestro. La meta de 4 GB y las pruebas de voz pertenecen a una validación posterior.

Paquetes finales locales de esta ejecución (excluidos de Git):

- `release/20261031-000320/Voxora/Voxora.exe`: aplicación portátil con carpetas `app` y `runtime`.
- `release/20261031-000326/Voxora-0.1.0.msi`: instalador para el usuario actual, con runtime incluido.
- Cada carpeta de distribución incluye `SHA256SUMS.txt`. Se comprobó que el JAR de la aplicación portátil coincide por SHA-256 con el JAR que superó la verificación final.

## Verificación realizada

- `scripts/build.ps1 -Offline`: compilación, pruebas y empaquetado JAR sobre Microsoft JDK 21. Resultado final: **31 pruebas aprobadas**, sin fallos ni omisiones (28 de servicios y 3 de JavaFX).
- Suite de servicios: flujo completo supervisor/técnico, contenido aprobado en PDF y DOCX, persistencia tras reabrir SQLite, autorización directa y catálogos, transacciones, credenciales, sesión, respaldos e interrupciones.
- Suite de JavaFX: primer arranque, creación de la cuenta temporal de soporte, navegación, listado, Kanban, configuración, diálogo de creación y guardado del editor del técnico al cerrarse.
- Imágenes generadas por pruebas en `target/ui-evidence/`: primer arranque, inicio, incidencias y configuración; revisadas para comprobar presentación. Son datos temporales de prueba, no capturas de la demo Python.
- Generación local de `app-image` y MSI con jpackage, runtime incluido y manifiestos SHA-256. No se ejecutó una instalación en otro equipo.

La herramienta de captura del escritorio identificó la ventana pero agotó el tiempo al intentar capturarla; la revisión visual utilizó imágenes de las escenas JavaFX generadas por las pruebas. No se presentan esas imágenes como una captura nativa de otro equipo.

La ejecución en classpath puede mostrar el aviso de configuración no modular de JavaFX, y POI puede emitir un aviso de proveedor de logging en consola. Las pruebas comprueban el funcionamiento de esas rutas; no se silencian errores de negocio con un resultado ficticio.

## Decisiones aplicadas para este incremento

- Un reporte por atención y múltiples versiones. La devolución mantiene la atención original.
- Separación de autor y aprobador. Solo el técnico responsable escribe el reporte.
- Prioridades CRITICA, ALTA, MEDIA y BAJA; especialidades MECANICA, ELECTRICA y GENERAL. Todas se validan en servicios y esquema.
- Crear exige título, equipo, ubicación y descripción; enviar exige al menos 20 caracteres como validación mínima, sin atribuirle garantía de calidad técnica.
- Generación PDF temporal y publicación antes de confirmar la aprobación; rollback ante fallos ordinarios y regeneración de documentos confirmados faltantes. No hay transacción distribuida ni garantía absoluta frente a fallos físicos del almacenamiento.
- Primer superadmin sin cuentas predeterminadas; administración de cuentas reservada a soporte en esta versión.
- Datos fuera del instalador, en el perfil local de Windows; ninguna sincronización de red o SOAP.

## Pendientes explícitos

Integración de IA tras resultados de VM; pruebas externas de instalación, micrófono y rendimiento; selección final de nombre/licencia propia; recuperación segura si se pierde el único acceso de soporte; evidencias y anexos; edición de datos iniciales de una incidencia; configuración granular de permisos; estadísticas avanzadas por periodo/técnico; cancelación y reapertura; extensiones de Dzone y migración de datos si se solicita.

La revisión de seguridad y uso real debe considerar esas limitaciones. No se han realizado commits, subidas a GitHub ni modificaciones de la demo Python. La configuración previa del IDE y los cambios del usuario permanecen en la copia de trabajo.
