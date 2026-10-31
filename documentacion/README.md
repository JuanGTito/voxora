# Documentación de Voxora 0.2.0

Revisión documental 01 del 31 de octubre de 2026. Documentos creados tras publicar el núcleo comprobado de Voxora.

- [Manual de usuario](<Voxora Manual de usuario.docx>): 5 páginas con capturas de la aplicación Java y recorrido por perfiles.
- [Guía de ejecución](<Voxora Guia de ejecucion.docx>): 4 páginas sobre instalación, preparación de modelos, pruebas, respaldo y distribución.
- [Guía del código fuente](<Voxora Guia del codigo fuente.docx>): 4 páginas sobre módulos, reglas, voz, persistencia, pruebas y referencias del equipo.

Las 13 páginas se exportaron con Microsoft Word instalado y se revisaron como imágenes. El renderizador basado en LibreOffice no estaba disponible en este equipo. Los PNG y PDF de revisión permanecen en `target/manual-qa` y no se distribuyen como documentos finales.

Código completo: [repositorio público](https://github.com/JuanGTito/voxora). El archivo local `Voxora_codigo_fuente_0.2.0.zip` se genera con `git archive` y contiene únicamente archivos versionados; se excluye de Git para no duplicar el repositorio. También se puede descargar el ZIP de código desde GitHub.

Para reconstruir los Word se utiliza `scripts/build-manuals.py` con python-docx y las imágenes/resultados de las pruebas locales. `scripts/render-manuals.ps1` usa Word y PDFBox para revisar páginas en Windows. Estas herramientas documentales no son requisitos para ejecutar Voxora.

Los ensayos automáticos incluyen motores reales con audio sintético y una corrección de texto proporcionada por la prueba. La captura de micrófono humano y el rendimiento de toda la solución en la VM de 4 GB siguen pendientes.
