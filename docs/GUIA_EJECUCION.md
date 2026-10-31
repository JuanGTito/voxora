# Guía de ejecución de Voxora 0.2.0

## Aplicación distribuida

Abrir `Voxora.exe` dentro de la carpeta portátil completa, conservando `app` y `runtime`. Cuando se prepara con `-IncludeAi`, los motores y modelos están en `app/ai` y se detectan automáticamente. No requiere JDK, Python, Ollama ni acceso a una API de IA en la nube para operar.

El MSI instala la aplicación con runtime para el usuario actual. Los modelos se copian por separado o se descargan en otro equipo mediante el script y se selecciona su carpeta desde Configuración. No se han instalado automáticamente ni se ha verificado el MSI en otro equipo. La prueba externa de Windows 10/11 permanece pendiente.

Crear la primera cuenta de soporte, sin credenciales predeterminadas. Desde Usuarios crear supervisor y técnicos; la especialidad es mecánica, eléctrica o general. Cerrar sesión para cambiar de identidad. Datos normales: `%LOCALAPPDATA%/Voxora`; otra cuenta de Windows utiliza otra ubicación salvo configuración explícita.

## Desde código

Requiere JDK 21. Maven Wrapper fija Maven 3.9.11; la primera compilación necesita red para las dependencias. Los scripts detectan JDK 21 en JAVA_HOME o en las instalaciones habituales, incluida `.jdks` de IntelliJ.

```powershell
./scripts/prepare-ai.ps1
./scripts/build.ps1
./scripts/run.ps1
```

`prepare-ai.ps1` descarga aproximadamente 2,6 GB en `.local-runtime`, comprueba SHA-256, extrae motores y conserva licencias. Es repetible: comprueba archivos completos y puede continuar descargas parciales. No ejecuta inferencia ni abre el micrófono. No hay descargas automáticas al transcribir o extraer.

`run.ps1 -DataDirectory <ruta>` aísla una instalación. `-AiHome <ruta>` permite otra carpeta de componentes. También existen `--data=<ruta>` y `--ai-home=<ruta>` en el lanzador Java, y la variable `VOXORA_AI_HOME` para los componentes.

## Voz y narración

Solo el técnico responsable de una incidencia En proceso puede preparar voz para ese reporte. Abrir la incidencia y pulsar Voz y narración.

1. Pulsar Grabar micrófono y después Detener y guardar, o Importar WAV. La grabación se limita a 180 segundos y guarda PCM en disco; importar convierte a mono, 16 kHz y 16 bits. La captura no empieza al abrir la ventana.
2. Pulsar Transcribir audio. Whisper Tiny genera el original que queda conservado con el audio. Si falla, el audio sigue disponible.
3. Corregir la narración revisable: comprobar nombres, números, negaciones y resultados. Las correcciones no sobrescriben el original.
4. Pulsar Organizar con IA. Qwen3 4B devuelve índices por actividades, materiales, pruebas, resultados, observaciones y hallazgos. Java valida y recupera las frases de la narración, sin aplicar prosa libre generada.
5. Revisar la propuesta y pulsar Usar como borrador. Si ya existe texto, confirmar su reemplazo; las versiones previas permanecen. Cambiar la narración invalida una propuesta anterior.
6. Corregir el reporte si hace falta, abrir vista previa y confirmar el envío. La IA no realiza ese envío ni la aprobación.

Las capturas y narraciones guardadas se recuperan en el selector superior. Al cerrar la ventana se cancela el procesamiento pendiente y se conserva lo ya guardado. Los límites operativos son 9000 caracteres, 100 oraciones, 240 segundos de transcripción y 300 segundos para cargar/consultar Qwen. La extracción utiliza CPU, un proceso temporal y un listener en 127.0.0.1 con clave efímera; el proceso termina al finalizar o cancelar.

## Revisar y conservar información

El supervisor consulta Revisiones, lee la versión enviada y devuelve con motivo o aprueba. Aprobar produce el PDF antes de confirmar el cierre. Si falta un documento de un cierre confirmado puede regenerarse desde la versión guardada. SQLite y los archivos no comparten una transacción distribuida; mantener respaldos y comprobar restauraciones.

Configuración permite ajustar el bloqueo de sesión entre 5 y 30 minutos, con 15 por defecto, y crear/restaurar respaldos. El ZIP incluye base, documentos y audios, con comprobaciones de ruta, tamaño, SHA-256 e integridad. No incluye los modelos ni está cifrado. Guardarlo en una ubicación protegida. Restaurar requiere soporte, reemplaza los datos y cierra sesiones.

La única cuenta de soporte no tiene una recuperación externa si pierde su contraseña: preparar otra cuenta autorizada y una política de recuperación antes del uso real. No hay contraseña maestra.

## Verificar y empaquetar

```powershell
./scripts/build.ps1 -Offline
./scripts/test-ai.ps1
./scripts/package.ps1 -Type app-image -IncludeAi -SkipBuild
./scripts/package.ps1 -Type msi -SkipBuild
```

`test-ai.ps1` usa una voz sintética española de Windows para producir el WAV; también admite `-AudioFixture` para un WAV conocido. No activa el micrófono. Los resultados quedan en `target/ai-evidence`, incluidas la transcripción, corrección proporcionada por la prueba, tiempos y PDF. `-SkipBuild` solo debe usarse después de verificar el JAR vigente.

El portátil con modelos es grande. El MSI deja los modelos fuera porque Qwen3 4B ocupa más de 2 GB en un solo archivo y excede el límite de archivo del formato CAB utilizado. No basta con copiar solo el EXE del portátil.

## Límites comprobados y pendientes

La muestra corta sintética permitió comprobar el recorrido hasta PDF con revisión. Whisper cometió un error de palabra y por eso se conserva la corrección humana como paso obligatorio. No se ha comprobado aún captura real, ruido, varios acentos ni narraciones de tres minutos en la VM de 4 GB. La solución local fue comprobada en un equipo de aproximadamente 32 GB; el límite de heap Java de 512 MB no mide el consumo de los procesos de IA.

Siguen fuera de esta versión hallazgos como flujo separado, preventivos, asignación automática, importación de Dzone, reapertura, anexos y editor de permisos granulares. El campo de extracción llamado hallazgos es texto dentro del borrador y no crea tickets por sí solo.
