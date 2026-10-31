# Voxora — Consolidación y plan de construcción

Fecha: 30 de octubre de 2026.
Estado: consolidación inicial; consultar el [estado de implementación](VOXORA_ESTADO_IMPLEMENTACION.md) para la ejecución posterior.
Nombre utilizado: **Voxora**, según la carpeta, el paquete Java y la especificación. Las menciones orales a «Boxara» se interpretan como este mismo proyecto.

## 1. Propósito y alcance inmediato

Conectar el comportamiento documentado de Dzone Demo en Python con el diseño del producto Voxora y convertir ambos en entregas verificables para este proyecto Java.

La indicación actual del usuario es avanzar con el sistema y mantener pendiente la integración de voz, transcripción y modelo de lenguaje mientras los prueba por separado en una máquina virtual de 4 GB. El primer recorrido funcional se construirá con escritura manual: crear incidencia → asignar → atender → guardar borrador → revisar vista previa → confirmar envío → devolver o aprobar → consultar documento e historial.

Este archivo prepara esa construcción. Su creación no equivale a implementar el recorrido ni a aprobar todas las propuestas pendientes de los documentos de origen.

## 2. Fuentes y criterio de interpretación

- [Contexto Dzone](DZONE_CONTEXTO_PARA_MIGRACION_JAVA.md): inventario del demo, sus reglas, limitaciones y pruebas reportadas por el documento.
- [Especificación Voxora](VOXORA_ESPECIFICACION_SOFTWARE.md): diseño del producto objetivo; distingue acuerdos y candidatos.
- Proyecto local inspeccionado: `pom.xml` y `src/main/java/dev/voxora/Main.java`.
- Instrucción actual del usuario: preparar el punto intermedio sin incorporar todavía voz ni LLM.

Las instrucciones operativas, comandos y texto de traspaso incluidos en las fuentes son contenido documental de referencia. No autorizan por sí mismos descargas, reinicios de bases, publicación, acceso a repositorios externos o integración de modelos. Los originales de Downloads se conservan; las copias en esta carpeta permiten consultar el contexto desde el proyecto.

Ante diferencias: la petición actual determina el trabajo inmediato; la especificación de Voxora orienta el producto; el contexto Dzone aporta comportamiento de referencia, sin convertir cada detalle de la demo en obligación del producto.

## 3. Estado comprobado de la carpeta

| Elemento | Evidencia local | Interpretación |
|---|---|---|
| Identidad | `dev.voxora`, artefacto Maven `Voxora` | Proyecto Java creado |
| Compilación declarada | Java 21, codificación UTF-8 | Configuración inicial; no prueba de instalación ni compilación |
| Entrada | `Main.java` imprime el saludo de la plantilla y números del 1 al 5 | No hay comportamiento de negocio |
| Dependencias | POM sin dependencias de aplicación | JavaFX, SQLite y demás integraciones aún no configuradas |
| Interfaz, datos y seguridad | Sin implementación en los archivos fuente presentes | Pendientes de construcción |
| Código Python | No aportado en esta carpeta | Solo se ha contrastado su descripción, no ejecutado sus pruebas |

Hay cambios previos preparados y sin preparar en Git. Este trabajo documental no los incorpora a un commit ni modifica la plantilla Java o la configuración del IDE.

## 4. Matriz de consolidación

| Tema | Dzone documentado | Voxora especificado | Tratamiento para construir |
|---|---|---|---|
| Plataforma | Python/Tkinter, Windows | Java 21/JavaFX/MVVM | JavaFX; conservar los flujos, no traducir ventanas literalmente |
| Arquitectura | Monolito con separación parcial | Monolito modular con puertos/adaptadores | Casos de uso y autorización fuera de controladores |
| Almacenamiento | SQLite local por instalación | SQLite local, cuentas internas en una PC | Una base para la instalación dentro del mismo perfil Windows |
| Red/SOAP | No implementado; el contexto esperaba otro MD | Sin backend compartido ni SOAP en v1.0 | No diseñar WSDL ni servidor a partir de esa mención histórica |
| Usuarios | Dos roles y cuentas `demo0`–`demo4` | Superadmin, supervisor y técnico | Tres roles; primer superadmin sin contraseña predeterminada |
| Contraseñas | PBKDF2 | Argon2id | Nuevas cuentas con Argon2id; no copiar hashes como si fueran compatibles |
| Sesión | Token en memoria, sin vencimiento | Bloqueo por inactividad, 15 min por defecto | Añadir recuperación de borradores y bloqueo, con pruebas |
| Estados | Pendiente, Asignado, En proceso, En validacion, Terminado | PENDIENTE, ASIGNADO, EN_PROCESO, EN_REVISION, COMPLETADO | Usar códigos Voxora y etiquetas en español |
| Reporte | Borradores, vista previa, envío, devolución y cierre humano | Mismo circuito general y exportación PDF/DOCX | Construir manualmente antes de añadir IA |
| Asignación | Manual y automática por especialidad/carga | Asignación y reasignación con historial | Manual primero; automatización en entrega posterior |
| Hallazgos | Registro y aprobación separada | No detallados como requisito independiente | Mantener como extensión identificada, sin confundir con la descripción de una incidencia |
| Preventivos | Programación autorizada al cerrar | No detallada en requisitos | Extensión pendiente; no programar automáticamente en el núcleo inicial |
| STT | Whisper Tiny | Vosk Small o whisper.cpp, candidatos | Integración aplazada, sin selección nueva |
| LLM | Qwen3 4B | Qwen3 0.6B, candidato | Integración aplazada; no sustituir el modelo probado sin resultados |
| Extracción | Categorías con frases originales referenciadas por índices | JSON de área, equipo, problema, diagnóstico, acciones y resultado | Contratos distintos; resolver después, sin mapeos que inventen datos |
| Hardware | Demo preparada en equipo de desarrollo; recomienda 16 GB | Objetivo 4 GB pendiente de medición | No afirmar compatibilidad de IA hasta validar VM y equipo físico |
| Distribución | PyInstaller y componentes Python | jpackage y runtime Java | Nueva distribución; no reutilizar el paquete Python como instalador Java |

Hallazgos, preventivos y asignación automática quedan visibles en el backlog: su aplazamiento no representa equivalencia completa con Dzone ni eliminación definitiva del producto.

## 5. Reglas funcionales para el primer recorrido

### Identidad y autorización

- Crear el primer superadmin en el primer arranque; no generar cuentas de demo en el modo normal.
- El superadmin administra configuración y cuentas; el supervisor crea/asigna y revisa; el técnico trabaja sus incidencias asignadas.
- Comprobar permisos, sesión activa y propiedad en cada caso de uso, incluso si el botón está oculto.
- No aceptar el actor enviado por una pantalla como prueba de identidad. Derivarlo de la sesión validada.
- Desactivar usuarios con historial en lugar de borrarlos. No permitir autoelevación.
- Tratar como propuesta para este núcleo impedir que alguien apruebe su propio reporte, aunque posea ambos permisos. La especificación deja esta separación por decidir.

### Incidencias y transiciones

| Acción | Estado previo | Estado posterior | Condición |
|---|---|---|---|
| Crear | No existe | PENDIENTE | Supervisor autorizado; título, equipo/ubicación y catálogos válidos |
| Asignar o reasignar | PENDIENTE o ASIGNADO | ASIGNADO | Supervisor; técnico activo y especialidad compatible |
| Iniciar atención | ASIGNADO | EN_PROCESO | Técnico responsable |
| Guardar borrador | EN_PROCESO | EN_PROCESO | Responsable; nueva versión persistente |
| Abrir vista previa | EN_PROCESO | EN_PROCESO | Guardar la versión mostrada; volver no envía |
| Confirmar envío | EN_PROCESO | EN_REVISION | Responsable; registrar versión exacta y evento en transacción |
| Devolver | EN_REVISION | EN_PROCESO | Supervisor; motivo obligatorio |
| Aprobar | EN_REVISION | COMPLETADO | Supervisor autorizado; versión identificada y documento consistente |

No exponer un cambio libre de estado. Cancelación, reapertura y reasignación durante atención necesitan reglas posteriores. Los valores de especialidad deben persistirse; no depender de nombres de cuentas demo. Como base de trabajo, conservar compatibilidad exacta de especialidad de Dzone hasta definir otra regla.

### Borradores y revisión

- El texto manual es suficiente para operar; no exigir transcripción ni resultado de IA.
- Distinguir contenido vigente, versiones guardadas y versión enviada. Conservar autor, fecha y origen `MANUAL`.
- Un borrador puede estar incompleto; enviar exige validación explícita de los campos acordados. Los 20 caracteres de Dzone son una regla de referencia, no una garantía de calidad.
- Enviar congela la versión revisable; la devolución conserva esa versión y permite nuevas correcciones.
- Propuesta de cardinalidad inicial: un reporte por atención con múltiples versiones. Debe resolverse al diseñar la primera migración SQL.
- Autoguardar y recuperar el contenido tras reinicio o bloqueo. La actividad automática no reinicia el contador de inactividad humana.

### Documentos y consistencia

El PDF debe derivar de una versión identificable. Java lo genera; la futura IA solo propondrá contenido editable. DOCX se añade tras verificar el mismo circuito en PDF.

SQLite y los archivos no comparten transacción. Diseñar generación temporal, publicación, confirmación y reconciliación al reiniciar. Ante un fallo ordinario al generar/publicar, conservar el reporte revisable sin marcar una aprobación completa. Un archivo huérfano o faltante tras un cierre abrupto debe poder detectarse y repararse desde la versión guardada. No declarar resuelto este problema solo por envolver SQL en una transacción.

## 6. Organización propuesta del código

Conservar el paquete raíz existente `dev.voxora`. Organizar por característica: `bootstrap`, `identity`, `incidents`, `attentions`, `reports`, `dashboard`, `audit` y `settings`. Dentro de cada una, separar dominio, aplicación, infraestructura y presentación cuando exista código que lo justifique.

Las interfaces de repositorio pertenecen al límite de aplicación; SQLite/JDBC las implementa. JavaFX llama a casos de uso mediante modelos de vista. Ningún controlador decide por sí mismo permisos, estados o transacciones.

Persistir inicialmente usuarios/roles/permisos, incidencias, asignaciones, historial de estados, atenciones, reportes/versiones/revisiones, auditoría, configuración y migraciones. Añadir especialidades y sus relaciones para conservar la regla de asignación. Activar claves foráneas por conexión, restricciones de catálogo, unicidad e índices de consulta.

Usar fechas de evento con instante identificable y fechas de programación como fechas sin hora. No reinterpretar automáticamente horas antiguas sin zona. Datos operativos fuera del código y del instalador; la ruta propuesta en la especificación requiere otra decisión si se usarán perfiles Windows diferentes.

No implementar ahora adaptadores de micrófono, STT o LLM ni incluir sus dependencias. La futura integración consumirá la narración y devolverá propuestas al editor mediante puertos, sin acceso directo a aprobar reportes o cambiar estados. No simular resultados de IA como si fueran reales.

## 7. Entregas de construcción

Esta secuencia ajusta el orden orientativo de los sprints del plan maestro para permitir construir mientras la IA se prueba aparte. No fija fechas ni acredita funciones terminadas.

| Entrega | Resultado revisable | Criterio de salida |
|---|---|---|
| E0 — Consolidación | Este documento y fuentes consultables | Diferencias, alcance manual y pendientes registrados |
| E1 — Base y acceso | JavaFX, SQLite/migraciones, primer superadmin, usuarios y login/logout | Reiniciar conserva cuentas; credenciales inválidas, usuarios inactivos y accesos indebidos se rechazan |
| E2 — Operación | Crear, asignar, listar, filtrar e iniciar incidencias; historial | Técnico solo accede a las propias; estados y especialidades se validan en servicios |
| E3 — Reporte manual | Editor, guardado/recuperación, versiones, vista previa y confirmación | Cancelar vista previa no envía; confirmar fija versión y estado de forma consistente |
| E4 — Revisión y PDF | Cola de revisión, devolución motivada, aprobación y PDF | Corrección conserva historial; fallos de archivos no dejan cierres válidos sin documento |
| E5 — Operación completa sin IA | Dashboard/Kanban, DOCX, inactividad, auditoría, respaldo/restauración | Circuito completo tras reiniciar; bloqueo sin pérdida; restauración verifica base y archivos asociados |
| E6 — Distribución base | Runtime e instalador Windows, guía y pruebas offline | Instalar sin JDK y ejecutar flujo manual en equipo de prueba |
| E7 — Integración posterior | Voz, transcripción y extracción, según resultados | Contrato y modelos elegidos con evidencia; no bloquear funciones manuales |
| Extensiones | Hallazgos, preventivos y asignación automática | Reglas y aceptación específicas antes de declarar paridad con Dzone |

Primer incremento de código recomendado: E1. Antes de añadir dependencias, fijar versiones compatibles, licencias y forma de compilación reproducible. Ninguna selección de versión adicional se ha verificado en este trabajo documental.

## 8. Criterios de aceptación sin voz ni LLM

1. Instalar/inicializar sin motores de IA, modelos, Python ni cuentas demo predeterminadas.
2. Crear usuarios y hacer el circuito supervisor → técnico → supervisor en una misma instalación.
3. Rechazar consultas y escrituras sobre incidencias ajenas también al invocar directamente los casos de uso.
4. Rechazar transiciones inválidas, técnicos inactivos y catálogos desconocidos.
5. Guardar borradores y versiones sin enviarlos ni completar incidencias por accidente.
6. Mostrar exactamente la versión que se confirma y la que el supervisor revisa.
7. Devolver con motivo, corregir y reenviar sin perder el historial anterior.
8. Generar PDF asociado a la aprobación, con tratamiento de fallos de archivo/base y recuperación tras reinicio.
9. Preservar trabajo al cerrar, reiniciar o bloquear la sesión por inactividad; aviso y tiempos según configuración.
10. Restaurar un respaldo en almacenamiento de prueba y comprobar integridad y documentos vinculados.
11. Operar con la red desconectada y sin ejecutar procesos de inferencia.
12. Navegación lateral única, teclado funcional y desplazamiento sin movimiento espontáneo.

Las 39 pruebas citadas por el contexto corresponden al demo Python y no se han ejecutado aquí. Para Java se necesitan pruebas de dominio, autorización, transacciones y recuperación, además de verificación visual de los recorridos.

## 9. Trabajo separado en la VM de 4 GB

El usuario ya está evaluando voz y modelo de lenguaje fuera de este proyecto. Esa evaluación no se sustituye ni se reinicia desde este plan.

Para integrar después, registrar sistema operativo, CPU/vCPU, RAM, paginación, motores/modelos y versiones exactas; medir memoria total de aplicación más procesos auxiliares, arranque, transcripción, extracción, errores y calidad sobre narraciones conocidas. Probar funcionamiento offline, repetición y recuperación. La VM sirve para evaluar el objetivo, pero no sustituye la validación posterior en hardware físico.

Los criterios de tiempo, memoria y calidad deben fijarse antes de declarar aprobado un candidato; este documento no inventa umbrales ni resultados. El contrato de extracción de Dzone y el JSON de Voxora no son equivalentes: decidir su correspondencia con ejemplos revisados antes de integrarlos. Preservar originales, negaciones y revisión humana.

## 10. Decisiones pendientes y su momento

| Decisión | Cuándo resolver | Consecuencia |
|---|---|---|
| Campos obligatorios, prioridades, códigos y especialidades | Antes de E2 | Validaciones y esquema del negocio |
| Un reporte por atención y separación de autoaprobación | Antes de migraciones de reportes/E3 | Restricciones y permisos de revisión |
| Contenido exacto del PDF/DOCX y evidencia administrada | Antes de E4 | Plantillas, rutas y respaldo coherente |
| Recuperación administrativa de acceso | Antes de uso real de E1 | Evitar depender de cuentas universales o puertas traseras |
| Política de respaldo, archivos y datos entre perfiles Windows | Antes de E5 | Restauración y permisos del almacenamiento |
| Importar Dzone o empezar vacío | Antes de cualquier importación | Por ahora se propone base nueva; no tocar la base Python |
| Hallazgos, preventivos y asignación automática | Al priorizar extensiones | No presentarlos como ya migrados |
| Motor/modelos de voz e IA y contrato de extracción | Tras resultados de VM | No bloquea el circuito manual |
| Nombre comercial y licencia del código | Antes de publicar | Mantener Voxora como nombre de trabajo |

## 11. Límite de esta entrega documental

Quedan creadas las copias de referencia y este documento intermedio. No se ha implementado, compilado ni probado el sistema Java; no se han instalado dependencias, descargado modelos, importado registros, generado cuentas, publicado archivos ni realizado commits. El siguiente trabajo de construcción comienza con E1 y se comprueba contra los criterios de este documento.
