# Código fuente y arquitectura de Voxora

La aplicación se organiza en un monolito modular de escritorio. La interfaz JavaFX usa modelos de vista y servicios; no contiene consultas SQL. El puerto `Repository` coordina operaciones transaccionales y `SqliteRepository` implementa SQLite. No hay backend de negocio, SOAP, sincronización LAN ni aplicación móvil.

| Carpeta | Responsabilidad |
|---|---|
| bootstrap | Rutas, bloqueo de instancia, inicialización y composición de servicios |
| domain | Usuarios, perfiles, estados, incidencias, versiones y reglas comunes |
| identity | Argon2id, sesiones, actividad humana y autorización |
| incidents | Creación, asignación, atención, versiones, envío y revisión |
| reports | Puerto documental, PDFBox y POI para PDF/DOCX |
| store | Puerto transaccional, implementación SQLite y migraciones |
| settings | Snapshot de base, respaldo, restauración y recuperación interrumpida |
| voice | Captura/importación WAV, verificación de componentes, procesos STT/LLM y propuestas |
| ui | Aplicación JavaFX, modelos de vista y diálogo de voz |

`Main` prepara cachés y lanza JavaFX. `AppContext` impide dos instancias con la misma carpeta, aplica migraciones y compone los servicios. `IdentityService` verifica la sesión en el servidor de casos de uso local; ocultar botones no sustituye la autorización. `IncidentService` expone acciones de negocio y no un cambio libre de estado.

`VoiceService` conserva original, corrección, hash de fuente y JSON de índices en `voice_entries`. Una versión aplicada enlaza `voice_entry_id`. `LlamaExtractor` valida categorías e índices y copia oraciones originales. Una salvaguarda conservadora aparta de pruebas las mediciones explícitamente no realizadas; no es una comprensión semántica completa. La revisión humana permanece necesaria.

Las migraciones V001 y V002 tienen checksums, claves foráneas y transacciones. No modificar una migración ya aplicada; añadir otra. Los respaldos antiguos compatibles se migran al restaurar. Las rutas de audio son relativas a los datos de la instalación.

## Compilar y probar

Maven Wrapper, JDK 21 y dependencias directas fijadas en `pom.xml`. `scripts/build.ps1` compila y ejecuta pruebas ordinarias. `scripts/test-ai.ps1` habilita la prueba explícita con motores y audio sintético. Las pruebas cubren permisos, sesiones, estados, borradores, vista previa, PDF/DOCX, respaldos, restauración, JSON, fuente cambiada, negaciones y pantallas JavaFX.

El código incluye comentarios colectivos LFTY, JGTM, SCGC, YARS y DMFY según [EQUIPO.md](EQUIPO.md). El generador usa el AST de javac y comprueba equivalencia antes/después. No renombra identificadores ni atribuye aportes individuales no indicados.

## Qué se publica

Código Java y pruebas, recursos, scripts de preparación/build, Maven Wrapper, diseño y documentación del producto. Se excluyen entornos, modelos, motores descargados, cachés, bases, audios, reportes operativos, respaldos, instaladores y configuración privada del IDE. Los nombres y revisiones oficiales de modelos están en `src/main/resources/ai/components.json`; no se suben los archivos grandes a Git.

El inventario privado de Dzone permanece local y no forma parte del producto distribuido. Los documentos históricos del plan describen decisiones iniciales; consultar [la guía vigente](GUIA_EJECUCION.md) para el comportamiento de la versión 0.2.0.
