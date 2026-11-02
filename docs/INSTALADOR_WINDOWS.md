# Instalador Windows de Voxora

## Para quien utiliza la aplicación

1. Descarga `Voxora-Setup-0.2.0-windows-x64.exe` de una distribución del equipo. El repositorio de código y el instalador son entregas distintas: descargar el ZIP de GitHub no instala el programa.
2. Abre el EXE. El asistente está en español. Lee la información, elige la carpeta y selecciona si quieres un icono en el escritorio.
3. Pulsa **Instalar** y después **Finalizar**. Puedes abrir Voxora al terminar o desde el menú Inicio.
4. En un equipo nuevo, crea tu administrador y guarda su contraseña. En el mismo perfil Windows se mantienen las cuentas existentes.

Incluye Java, Whisper Tiny, llama.cpp y Qwen3 4B Q4_K_M. La instalación y la voz pueden funcionar sin conexión; no requiere consola, Python ni .NET. Destino predeterminado: `%LOCALAPPDATA%\Programs\Voxora`. Instala para el usuario actual, sin exigir permisos de administrador. Requiere Windows x64 y al menos 6 GB libres durante la instalación; reserva espacio adicional para datos. El rendimiento con solo 4 GB de RAM sigue pendiente de validación.

Para quitarlo, utiliza **Configuración de Windows → Aplicaciones → Voxora → Desinstalar**. La base de datos, cuentas, audios y documentos permanecen en `%LOCALAPPDATA%\Voxora`. Cierra Voxora antes de actualizar y crea un respaldo desde la aplicación.

Esta compilación no lleva firma digital de editor. Windows puede mostrar una advertencia de editor desconocido. Comprueba el origen y el SHA-256 que acompaña al archivo antes de ejecutarlo.

## Para quien desarrolla

Requisitos: Windows x64, JDK 21, conexión en la primera construcción y espacio para las copias de empaquetado (recomendado 15 GB libres). Maven Wrapper está incluido. No se necesita un proyecto .NET.

Doble clic en `Crear instalador.cmd`, o desde PowerShell en la raíz:

```powershell
.\scripts\build-installer.ps1
```

El script ejecuta pruebas, verifica o descarga modelos con SHA-256, crea la imagen Java con `jpackage` y compila el asistente con Inno Setup 7.1.1. Descarga el compilador oficial con versión y SHA-256 fijados y comprueba su firma Authenticode; lo prepara en `.build-cache`. Su instalador puede registrar allí el compilador para el usuario actual. Los modelos y artefactos grandes quedan fuera de Git.

Resultado: `release\<fecha-hora>\installer\Voxora-Setup-0.2.0-windows-x64.exe` y su `.sha256`. Para reconstruir con dependencias ya preparadas:

```powershell
.\scripts\build-installer.ps1 -Offline
# O usar un compilador ya instalado:
.\scripts\build-installer.ps1 -CompilerPath 'C:\ruta\ISCC.exe'
```

`installer/windows/voxora.iss` define pantallas, accesos directos y desinstalación. El icono está en `src/main/resources/icons`. Inno Setup conserva sus avisos de autoría en el asistente; licencias de Java, bibliotecas, modelos y motores se incluyen con la distribución. Para publicar una nueva versión, cambia las versiones en `pom.xml`, `scripts/package.ps1`, `scripts/build-installer.ps1` y el archivo ISS conjuntamente.

La publicación del EXE necesita un canal de distribución para archivos grandes. Un repositorio público de código no publica automáticamente el instalador. Esta construcción local no crea un Release ni sube los modelos a GitHub.

## Comprobación local del 1 de noviembre de 2026

Se generó un EXE de 2.729.251.299 bytes. SHA-256: `7463ba8866ed1b8061a5aba608b516b57d2ab5d7e6fe5fdce9b821f07afff539`.

La construcción ejecutó 50 pruebas: 48 correctas y 2 pruebas de motores reales omitidas en esta ejecución ordinaria. Se instaló silenciosamente en una carpeta aislada, se compararon por SHA-256 los 483 archivos de la imagen original con los instalados, se abrió Voxora con datos de prueba y se comprobó que creara su base SQLite. La desinstalación retiró el programa y su registro de Windows, conservando la base de prueba sin cambios. No se modificaron las cuentas de trabajo. Los registros están en `.build-cache/installer-test.log` y `.local-data/installer-check-20261101/`.

Esta comprobación cubre instalación silenciosa, integridad, arranque y desinstalación. Sigue pendiente una revisión manual de cada pantalla del asistente y la prueba de rendimiento en una VM de 4 GB.
