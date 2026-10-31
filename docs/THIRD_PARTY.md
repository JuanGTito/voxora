# Dependencias de la aplicación

Versiones directas fijadas en `pom.xml`. Las dependencias transitivas se resuelven mediante Maven Central. Los JAR distribuidos conservan sus recursos de licencia y avisos en `META-INF`; el runtime generado conserva sus avisos legales. La licencia del código propio sigue pendiente de elección y esta lista no concede derechos sobre él.

| Componente | Versión | Licencia / referencia del proyecto |
|---|---|---|
| JavaFX controls/base/graphics | 21.0.10 | GPLv2 con Classpath Exception — https://openjfx.io/ |
| SQLite JDBC Xerial | 3.51.2.0 | Apache-2.0 / BSD-2-Clause según componentes — https://github.com/xerial/sqlite-jdbc |
| Bouncy Castle bcprov | 1.83 | Licencia Bouncy Castle, de tipo MIT — https://www.bouncycastle.org/licence.html |
| PDFBox | 3.0.6 | Apache-2.0 — https://pdfbox.apache.org/ |
| Apache POI | 5.4.1 | Apache-2.0 — https://poi.apache.org/ |
| SLF4J NOP | 2.0.17 | MIT — https://www.slf4j.org/ |
| JUnit Jupiter (solo pruebas) | 5.12.2 | EPL-2.0 — https://junit.org/junit5/ |
| Jackson databind | 2.20.1 | Apache-2.0 — https://github.com/FasterXML/jackson-databind |
| whisper.cpp CPU Windows | b5454 | MIT — https://github.com/ggml-org/whisper.cpp |
| Whisper Tiny multilingüe | revisión 5359861c739e955e79d9a303bcbc70fb988958b1 | MIT — https://huggingface.co/ggerganov/whisper.cpp |
| llama.cpp CPU Windows | b11838 | MIT — https://github.com/ggml-org/llama.cpp |
| Qwen3 4B Q4_K_M | revisión bc640142c66e1fdd12af0bd68f40445458f3869b | Apache-2.0 — https://huggingface.co/Qwen/Qwen3-4B-GGUF |

JavaFX se distribuye con sus bibliotecas nativas de Windows. Los motores y modelos se preparan con scripts/prepare-ai.ps1; package.ps1 -IncludeAi puede incluirlos en la aplicación portátil junto con sus licencias. No se suben motores descargados, modelos ni audios a Git ni se incluyen componentes Python del demo. El PDF puede usar Segoe UI instalado en Windows; no se copia ni redistribuye ese archivo de fuente. En sistemas sin esa fuente se usa Helvetica estándar de PDFBox; los caracteres no representables se sustituyen en el PDF, conservando el texto original de la versión en SQLite y DOCX.

Fuentes técnicas consultadas para la implementación: [JavaFX/Maven](https://openjfx.io/openjfx-docs/maven), [Argon2 de Bouncy Castle](https://github.com/bcgit/bc-java/blob/main/core/src/main/java/org/bouncycastle/crypto/generators/Argon2BytesGenerator.java), [PDFBox 3](https://pdfbox.apache.org/3.0/migration.html), [POI XWPF](https://poi.apache.org/components/document/quick-guide-xwpf.html) y [SQLite JDBC](https://github.com/xerial/sqlite-jdbc).
