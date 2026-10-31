# Guía histórica del incremento manual 0.1.0

Consultar [GUIA_EJECUCION.md](GUIA_EJECUCION.md) para la versión 0.2.0 con voz local.

## Distribución

El MSI generado en `release/<fecha-hora>/Voxora-0.1.0.msi` instala para el usuario actual e incluye el runtime. La variante `app-image` genera `release/<fecha-hora>/Voxora/Voxora.exe` con las carpetas `app` y `runtime`. Copiar la carpeta completa para usar esa variante. No se ha instalado automáticamente el MSI en el equipo del usuario.

Los archivos `SHA256SUMS.txt` permiten comprobar los binarios entregados; no constituyen una firma digital ni un certificado de confianza del instalador. Los paquetes locales no están firmados. WiX se usa solo durante el empaquetado MSI, no al ejecutar Voxora.

## Desarrollo

Abrir `pom.xml` en IntelliJ con JDK 21. La clase de entrada es `dev.voxora.Main`. Desde PowerShell puede ejecutarse `./scripts/build.ps1` y luego `./scripts/run.ps1`; alternativamente, abrir `Iniciar Voxora.cmd`.

La preparación inicial requiere red para Maven Central; la operación de negocio, la generación de documentos y los respaldos son locales. No existe descarga de modelos ni servidor de negocio. El programa limita su heap a 512 MB en los lanzadores suministrados; ese límite no equivale a medir la memoria total del proceso ni a certificar el objetivo de 4 GB.

`scripts/package.ps1 -Type msi` necesita `candle.exe` y `light.exe` de WiX 3 en PATH, o sus herramientas portátiles en `.build-cache/wix3141/tools`. En esta construcción se utilizó el [ZIP oficial de WiX 3.14.1](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm), sin instalar WiX en Windows. SHA-256 del ZIP descargado: `6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31`. No se distribuye ese ZIP en Git.

## Primer arranque

1. Crear un usuario de soporte de 3–40 caracteres: letras sin tildes, números, punto, guion o guion bajo. La contraseña debe tener de 10 a 256 caracteres y se confirma en pantalla.
2. En Usuarios crear una cuenta de supervisor y al menos un técnico con especialidad mecánica, eléctrica o general.
3. Cerrar sesión para cambiar de cuenta. La identidad y permisos proceden de la sesión; no hay selector libre de rol.

Las contraseñas se guardan como Argon2id con sal aleatoria individual, incluyendo los parámetros en el hash. Soporte puede crear otro superadministrador y restablecer contraseñas; esto revoca las sesiones de la cuenta afectada. No existe una contraseña maestra ni una recuperación externa para una instalación cuyo único superadministrador perdió el acceso. Definir ese mecanismo antes de uso real.

## Recorrido de trabajo

1. Supervisor: crear incidencia con título, equipo, ubicación, descripción, prioridad y especialidad.
2. Abrir incidencia y asignar un técnico activo de especialidad exacta. Solo se reasigna antes de comenzar la atención.
3. Técnico: ingresar con su cuenta, consultar Incidencias y abrir la tarea; iniciar atención.
4. Escribir el trabajo realizado. El editor guarda cambios cada 5 segundos y al cerrar normalmente; Guardar borrador crea una revisión cuando el contenido cambió.
5. Abrir Vista previa y envío. Se guarda la versión mostrada. Volver y corregir conserva el borrador sin enviarlo. Confirmar envío cambia el estado a En revisión.
6. Supervisor: abrir Revisiones. Solicitar corrección exige motivo y devuelve el trabajo al técnico, conservando versiones. Aprobar genera el PDF y completa la incidencia.
7. Una incidencia completada permite abrir PDF o DOCX de su versión aprobada. La exportación registra auditoría. No se puede aprobar el reporte propio.

El contenido enviado queda asociado a una versión inmutable; una vista previa antigua no puede enviarse si el borrador cambió. El guardado automático no convierte actividad de fondo en actividad humana. Ante un cierre abrupto puede perderse el texto escrito desde el último guardado; se recupera lo persistido.

## Datos y respaldos

Ruta normal: `%LOCALAPPDATA%/Voxora`. Contiene `data/voxora.db`, documentos en `reports`, caché local de bibliotecas nativas y un archivo de bloqueo de instancia. `--data=<ruta>` permite otra instalación aislada para pruebas. No usar simultáneamente dos procesos con la misma carpeta. Distintas cuentas de Windows no comparten esa ruta automáticamente.

En Configuración, soporte puede crear un ZIP con snapshot consistente de SQLite, documentos y manifiesto SHA-256. El nombre de destino debe ser nuevo. Guardar la copia en una ubicación protegida. Los respaldos contienen los datos de la instalación y no están cifrados.

Restaurar requiere soporte y confirmación explícita en la aplicación. Se verifican rutas, límites de tamaño, hashes, integridad SQLite, claves foráneas, versión de esquema y existencia de un superadministrador activo. El respaldo admite hasta 1 GB total y 256 MB por archivo. La restauración reemplaza datos y documentos, conserva una copia temporal para recuperar una interrupción y cierra las sesiones. Entrar con una cuenta del respaldo.

Los checksums detectan corrupción; no prueban quién creó un respaldo. Solo restaurar copias propias de procedencia conocida. Mantener respaldos externos: la copia temporal de recuperación no es un archivo histórico permanente.

## Sesión

Bloqueo predeterminado a los 15 minutos, configurable por soporte entre 5 y 30. El aviso aparece durante el minuto previo y se comprueba cada 5 segundos. Interacciones de teclado, clic o desplazamiento actualizan la actividad; autoguardado no lo hace. El nuevo acceso requiere login.

## Alcance pendiente

Voz, transcripción y LLM permanecen fuera de esta distribución. También quedan pendientes hallazgos, preventivos, asignación automática, importación de Dzone y validación externa en Windows 10/11 y hardware de 4 GB. Los roles usan plantillas de permisos persistidas; no hay editor de permisos granulares en pantalla. Esta versión no administra anexos o evidencias de archivos, cancelación ni reapertura.
