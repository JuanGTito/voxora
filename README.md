# Voxora

Aplicación de escritorio Windows para incidencias técnicas y reportes con voz local. Java 21, JavaFX y SQLite; Whisper Tiny para transcripción y Qwen3 4B Q4_K_M mediante llama.cpp para organizar frases de la narración. El técnico corrige y envía; el supervisor revisa y aprueba.

## Empezar

Para instalar con asistente y modelos incluidos, consulta [Instalador Windows](docs/INSTALADOR_WINDOWS.md). Para desarrolladores: `Crear instalador.cmd` genera el EXE completo.

El instalador completo EXE incluye runtime Java, motores y modelos de voz. La aplicación portátil y el MSI también incluyen Java; el MSI antiguo requiere preparar los modelos aparte. En la primera ejecución crea un superadministrador y después cuentas de supervisor y técnicos. No hay contraseñas predeterminadas. Las cuentas de Voxora comparten los datos dentro del mismo perfil Windows.

Desde el código, instala JDK 21 y abre `Iniciar Voxora.cmd`, o utiliza PowerShell:

```powershell
./scripts/prepare-ai.ps1
./scripts/build.ps1
./scripts/run.ps1
```

La preparación descarga aproximadamente 2,6 GB y verifica versiones y SHA-256. Los motores, modelos y datos se guardan en carpetas ignoradas por Git. Después de preparar el entorno, la inferencia y el flujo de negocio son locales. No se usa una API de IA en la nube.

En una instalación desde MSI, configura la carpeta preparada de IA en Configuración. La aplicación portátil creada con `-IncludeAi` encuentra sus modelos incluidos automáticamente. El instalador MSI distribuye la aplicación; los modelos se preparan o copian por separado.

## Flujo

La interfaz local actual ofrece **Hablar → Detener → Revisar** en nueva incidencia y atención del técnico. Al detener, transcribe y organiza automáticamente; el resultado se puede editar antes de confirmar. Consulta [el flujo simplificado](docs/FLUJO_VOZ_SIMPLIFICADO.md) para los cambios respecto a los manuales anteriores.

1. Supervisor crea la incidencia y asigna un técnico de especialidad compatible.
2. Técnico inicia atención y abre Voz y narración, o escribe el reporte directamente.
3. Graba con una acción explícita o importa un WAV; transcribe, corrige y organiza el texto.
4. Revisa la propuesta y úsala como borrador. La IA selecciona frases originales mediante índices; no decide estados ni aprueba.
5. Vista previa y confirmación envían la versión exacta al supervisor.
6. Supervisor devuelve con motivo o aprueba y genera PDF. El reporte completado también permite DOCX.

Incluye historial de asignaciones y estados, borradores con versiones, Kanban, auditoría, bloqueo por inactividad y respaldo/restauración de SQLite, documentos y audios. Los motores fallidos no impiden trabajar con texto manual.

## Pruebas y distribución

```powershell
# Pruebas sin depender de modelos preparados
./scripts/build.ps1 -Offline

# Suite completa y motores reales con audio sintético español
./scripts/test-ai.ps1

# Datos aislados para una prueba manual
./scripts/run.ps1 -DataDirectory "$PWD/.local-data/prueba"

# Distribución portátil con runtime, motores y modelos
./scripts/package.ps1 -Type app-image -IncludeAi

# Instalador Windows con asistente, runtime, motores y modelos
./scripts/build-installer.ps1

# MSI con runtime; modelos por separado; necesita WiX 3
./scripts/package.ps1 -Type msi
```

La prueba real usa una muestra sintética corta, conserva la transcripción original y simula una corrección humana antes de organizarla. No prueba acentos, ruido ni captura con micrófono real. La compatibilidad de toda la solución con 4 GB sigue pendiente; la comprobación local se realizó en un equipo con aproximadamente 32 GB. Los archivos de `target/ai-evidence` contienen resultados locales reproducibles.

## Documentación

- [Manual visual de instalación en Word con capturas y flechas](documentacion/Voxora%20Manual%20visual%20de%20instalacion%20APA.docx).
- [Instalador Windows y construcción del EXE](docs/INSTALADOR_WINDOWS.md).
- [Guía de ejecución](docs/GUIA_EJECUCION.md).
- [Validación de voz y límites](docs/VALIDACION_VOZ.md).
- [Código y arquitectura](docs/CODIGO_FUENTE.md).
- [Equipo y referencias en comentarios](docs/EQUIPO.md).
- [Dependencias y licencias de terceros](docs/THIRD_PARTY.md).
- [Plan inicial de construcción](docs/VOXORA_PLAN_CONSTRUCCION.md).

La documentación Word se encuentra en `documentacion/`. La demo Python y su inventario privado se mantienen como referencia local; no se publican sus datos ni se importan automáticamente. Las decisiones históricas de diseño no deben interpretarse como funciones ya terminadas.
