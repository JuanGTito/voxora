"""LFTY JGTM SCGC YARS DMFY — Rebuild the three Word manuals from verified local evidence."""
from pathlib import Path
import json
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "documentacion"
EVIDENCE = ROOT / "target" / "ui-evidence"
RESULT = json.loads((ROOT / "target/ai-evidence/local-models.json").read_text(encoding="utf-8"))
TEAM = [("Luis Fernando Ticona Yunganina", "LFTY"), ("Juan Gedeon Tito Moya", "JGTM"),
        ("Smith Clever Givera Coila", "SCGC"), ("Yuri Andrea Ramos Suca", "YARS"),
        ("Dimas Mijael Flores Yerba", "DMFY")]


def new(title, audience):
    doc = Document()
    section = doc.sections[0]
    section.page_width, section.page_height = Inches(8.27), Inches(11.69)
    section.top_margin = section.bottom_margin = Inches(.75)
    section.left_margin = section.right_margin = Inches(.8)
    for name in ("Normal", "Title", "Subtitle", "Heading 1", "Heading 2"):
        style = doc.styles[name]
        style.font.name = "Calibri"
        style.font.color.rgb = RGBColor(0, 0, 0)
    doc.styles["Normal"].font.size = Pt(10.5)
    doc.styles["Normal"].paragraph_format.space_after = Pt(7)
    doc.styles["Normal"].paragraph_format.line_spacing = 1.08
    doc.styles["Title"].font.size = Pt(26)
    doc.styles["Heading 1"].font.size = Pt(18)
    doc.styles["Heading 2"].font.size = Pt(12)
    doc.core_properties.title = title
    doc.core_properties.author = "Equipo Voxora"
    doc.core_properties.subject = audience
    header = section.header.paragraphs[0]
    header.text = "VOXORA 0.2.0    |    " + audience
    header.style = doc.styles["Normal"]
    for run in header.runs:
        run.font.size = Pt(8)
    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    footer.add_run("31 de octubre de 2026    |    Página ").font.size = Pt(8)
    field = OxmlElement("w:fldSimple")
    field.set(qn("w:instr"), "PAGE")
    footer._p.append(field)
    doc.add_paragraph(title, "Title")
    p(doc, "Versión 0.2.0 · Escritorio Windows · Revisión documental 01")
    return doc


def p(doc, text):
    return doc.add_paragraph(text)


def heading(doc, text, page=False):
    if page:
        doc.add_page_break()
    doc.add_heading(text, level=1)


def steps(doc, values):
    for index, text in enumerate(values, 1):
        p(doc, f"{index}. {text}")


def table(doc, headers, rows, widths):
    result = doc.add_table(rows=1, cols=len(headers))
    result.alignment = WD_TABLE_ALIGNMENT.CENTER
    result.autofit = False
    for row in result.rows:
        for cell, width in zip(row.cells, widths):
            cell.width = Inches(width)
    for cell, text in zip(result.rows[0].cells, headers):
        cell.text = text
    for values in rows:
        for cell, text in zip(result.add_row().cells, values):
            cell.text = str(text)
    for i, row in enumerate(result.rows):
        for j, cell in enumerate(row.cells):
            cell.width = Inches(widths[j])
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            properties = cell._tc.get_or_add_tcPr()
            shading = OxmlElement("w:shd")
            shading.set(qn("w:fill"), "E8EDF3" if i == 0 else ("FFFFFF" if i % 2 else "F7F9FC"))
            properties.append(shading)
            margins = OxmlElement("w:tcMar")
            for side in ("top", "left", "bottom", "right"):
                node = OxmlElement(f"w:{side}")
                node.set(qn("w:w"), "95")
                node.set(qn("w:type"), "dxa")
                margins.append(node)
            properties.append(margins)
            borders = OxmlElement("w:tcBorders")
            for side in ("top", "left", "bottom", "right"):
                edge = OxmlElement(f"w:{side}")
                for key, value in (("val", "single"), ("sz", "4"), ("color", "D9D9D9")):
                    edge.set(qn(f"w:{key}"), value)
                borders.append(edge)
            properties.append(borders)
            for paragraph in cell.paragraphs:
                paragraph.paragraph_format.space_after = Pt(2)
                paragraph.paragraph_format.space_before = Pt(2)
                for run in paragraph.runs:
                    run.font.size = Pt(9.5)
                    run.bold = i == 0
    repeat = OxmlElement("w:tblHeader")
    result.rows[0]._tr.get_or_add_trPr().append(repeat)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)


def figure(doc, file, caption, width=6.35):
    paragraph = doc.add_paragraph()
    paragraph.paragraph_format.space_after = Pt(4)
    paragraph.add_run().add_picture(str(EVIDENCE / file), width=Inches(width))
    description = paragraph._p.xpath('.//wp:docPr')
    if description:
        description[0].set("descr", caption)
    caption_p = p(doc, caption)
    for run in caption_p.runs:
        run.italic, run.font.size = True, Pt(9)


def code(doc, value):
    paragraph = doc.add_paragraph()
    paragraph.paragraph_format.space_after = Pt(10)
    run = paragraph.add_run(value)
    run.font.name, run.font.size = "Consolas", Pt(9)


def save(doc, name):
    # Remove template title borders; titles use plain black typography.
    for element in (doc.styles.element, doc.element):
        for border in list(element.xpath('.//w:pBdr')):
            border.getparent().remove(border)
    OUT.mkdir(exist_ok=True)
    destination = OUT / name
    doc.save(destination)
    print(destination)


def user_manual():
    doc = new("Manual de usuario de Voxora", "Uso por supervisor y técnico")
    p(doc, "Este manual explica cómo registrar incidencias, asignar trabajo, preparar un reporte con voz o texto y obtener un documento aprobado. Voxora funciona en una PC con cuentas internas. El técnico envía su versión y el supervisor decide el cierre; la inteligencia artificial propone contenido revisable.")
    table(doc, ("Perfil", "Responsabilidad"), [("Soporte", "Inicializa, administra usuarios, modelos, sesión y respaldos."), ("Supervisor", "Crea, asigna, revisa, devuelve o aprueba incidencias."), ("Técnico", "Atiende las incidencias propias y prepara sus reportes.")], [1.25, 5.1])
    figure(doc, "dashboard.png", "Figura 1 Inicio de la aplicación Java con datos sintéticos de prueba.")

    heading(doc, "Crear el acceso y cambiar de usuario", True)
    steps(doc, ["Abrir Voxora.exe desde la carpeta completa o utilizar el acceso instalado. En una instalación nueva aparece Prepara tu instalación.", "Crear la cuenta de soporte con usuario de 3 a 40 caracteres y contraseña de 10 a 256 caracteres. Repetirla para confirmar. No hay claves predeterminadas.", "En Usuarios, soporte crea supervisor y técnicos, indicando la especialidad. Cerrar sesión antes de entrar con otra cuenta.", "Conservar al menos un soporte activo. Si se pierde el único acceso no existe una contraseña maestra de recuperación."])
    figure(doc, "first-run.png", "Figura 2 Primer arranque sin cuentas ni credenciales predefinidas.", 5.9)

    heading(doc, "Crear y asignar una incidencia", True)
    steps(doc, ["Supervisor abre Incidencias y pulsa Nueva incidencia. Completa título, equipo, ubicación, prioridad, especialidad y descripción.", "La incidencia queda Pendiente. Abrirla y seleccionar un técnico activo con la misma especialidad. General no sustituye automáticamente a Mecánica o Eléctrica.", "Antes de iniciar la atención puede cambiarse la asignación. No se reasigna un trabajo que ya está En proceso.", "El técnico entra a su cuenta, abre la incidencia e inicia atención. Solo ve y modifica sus propias tareas."])
    figure(doc, "incidents.png", "Figura 3 Consulta y búsqueda por código, título, equipo, ubicación o técnico.")
    p(doc, "El tablero muestra las mismas incidencias por estado. Las tarjetas abren su detalle; no permiten saltarse las transiciones del negocio.")

    heading(doc, "Preparar el reporte con voz o texto", True)
    steps(doc, ["En una incidencia En proceso, abrir Voz y narración. Pulsar Grabar micrófono y luego Detener y guardar, o importar un WAV. La ventana no inicia la captura por sí sola.", "Transcribir audio y corregir la narración. Comprobar nombres, números y negaciones: Whisper Tiny puede equivocarse. El audio y el texto original se conservan.", "Organizar con IA, revisar la propuesta y usarla como borrador. Cambiar la narración invalida la propuesta anterior. Aplicar no envía ni aprueba el reporte."])
    figure(doc, "voice.png", "Figura 4 Narración editable y propuesta revisable con frases originales. Datos sintéticos.", 6.0)
    p(doc, "También puede escribirse directamente en el editor del reporte. El guardado automático funciona cada cinco segundos; las versiones guardadas permiten recuperar lo persistido.")

    heading(doc, "Enviar revisar y conservar el resultado", True)
    steps(doc, ["El técnico abre Vista previa y envío y comprueba el contenido. Volver y corregir conserva el borrador. Solo Confirmar envío a supervisión cambia el estado a En revisión.", "El supervisor abre Revisiones y lee la versión enviada. Solicitar corrección exige motivo; el técnico corrige y vuelve a enviar sin borrar el historial.", "Aprobar y generar PDF completa la incidencia. No se permite aprobar el reporte propio. Después pueden abrirse PDF y DOCX.", "Soporte puede crear un respaldo de base, documentos y audios desde Configuración. Restaurar reemplaza la información y cierra las sesiones."])
    figure(doc, "settings.png", "Figura 5 Configuración de sesión, componentes locales y respaldo.", 5.7)
    p(doc, "Si voz o IA fallan, conservar lo ya guardado y continuar con texto manual. La validación local usó audio sintético; falta probar el micrófono real, ruido, acentos y la VM de 4 GB.")
    save(doc, "Voxora Manual de usuario.docx")


def execution_guide():
    doc = new("Guía de ejecución de Voxora", "Instalación y operación local")
    p(doc, "Esta guía permite abrir una distribución preparada o compilar Voxora 0.2.0 desde su código. La aplicación y los motores de inferencia funcionan localmente después de preparar sus componentes. Las pruebas disponibles no certifican todavía el objetivo de una máquina de 4 GB.")
    table(doc, ("Elemento", "Condición"), [("Plataforma objetivo", "Windows x64. Instalación externa en Windows 10 y 11 pendiente."), ("Distribución portátil", "Carpeta completa con Voxora.exe, app y runtime."), ("MSI", "Incluye Java. Motores y modelos se preparan por separado."), ("Compilación", "JDK 21 y Maven Wrapper. Red para la preparación inicial."), ("Comprobación local", "Equipo con aproximadamente 32 GB de RAM y motores CPU.")], [1.5, 4.85])
    heading(doc, "Abrir la distribución")
    steps(doc, ["Conservar toda la carpeta portátil y abrir Voxora.exe. El ejecutable aislado no contiene sus dependencias.", "Crear el primer soporte y después cuentas de supervisor y técnicos. El programa no incluye contraseñas predeterminadas.", "Si el portátil contiene app/ai, detecta los modelos. En MSI, seleccionar la carpeta preparada desde Configuración."])
    p(doc, "Datos normales: %LOCALAPPDATA%/Voxora. Las cuentas de la aplicación comparten esa base dentro del mismo perfil Windows. Otra cuenta de Windows no comparte esa ruta automáticamente.")

    heading(doc, "Preparar motores y modelos", True)
    p(doc, "Desde el proyecto, ejecutar el script de preparación. Descarga aproximadamente 2,6 GB, comprueba SHA-256 y conserva avisos de licencia. Puede repetirse para comprobar archivos o continuar descargas parciales. No activa el micrófono ni realiza inferencia.")
    code(doc, "./scripts/prepare-ai.ps1\n./scripts/build.ps1\n./scripts/run.ps1")
    table(doc, ("Componente", "Versión fijada", "Tamaño aproximado"), [("whisper.cpp Windows CPU", "b5454", "9 MB comprimidos"), ("llama.cpp Windows CPU", "b11838", "24 MB comprimidos"), ("Whisper Tiny", "ggml-tiny.bin", "78 MB"), ("Qwen3 4B", "Q4_K_M GGUF", "2,50 GB")], [2.0, 2.8, 1.55])
    p(doc, "Las revisiones y hashes completos están en src/main/resources/ai/components.json. Los archivos preparados quedan en .local-runtime y no se publican en Git. La inferencia normal nunca descarga modelos.")
    p(doc, "run.ps1 -AiHome permite otra carpeta de modelos; -DataDirectory permite una instalación de prueba aislada. El lanzador admite --ai-home y --data. También se puede configurar VOXORA_AI_HOME.")

    heading(doc, "Comprobar el funcionamiento", True)
    code(doc, "./scripts/build.ps1 -Offline\n./scripts/test-ai.ps1")
    p(doc, "El primer comando comprueba la aplicación sin depender de modelos. El segundo habilita expresamente la integración real y genera una muestra sintética española con Windows; no graba el micrófono. Puede indicarse un WAV conocido con -AudioFixture.")
    table(doc, ("Verificación final", "Resultado"), [("Casos de negocio y seguridad", "28 aprobados"), ("Voz y validadores", "11 aprobados"), ("Pantallas JavaFX", "4 aprobados"), ("Motores reales y circuito hasta PDF", "1 aprobado"), ("Total", "44 aprobados sin omisiones")], [3.7, 2.65])
    p(doc, f"En la muestra corta, Whisper tardó {RESULT['transcriptionSeconds']:.2f} segundos y la extracción con Qwen {RESULT['extractionSeconds']:.2f} segundos. Son observaciones de este equipo, no tiempos garantizados.")
    p(doc, "El original contenía «No se medio presión». La prueba conserva ese texto y proporciona una corrección que simula la revisión humana antes de extraer. No se presenta la corrección como resultado automático del modelo.")
    p(doc, "Los detalles y el PDF sintético están en target/ai-evidence. El reconocimiento de formato compatible de micrófono no equivale a probar la captura real. Pendientes: ruido, acentos, tres minutos y memoria total en la VM de 4 GB.")

    heading(doc, "Empaquetar respaldar y resolver fallos", True)
    code(doc, "./scripts/package.ps1 -Type app-image -IncludeAi -SkipBuild\n./scripts/package.ps1 -Type msi -SkipBuild")
    p(doc, "Usar -SkipBuild solo después de verificar el JAR vigente. MSI necesita WiX 3. El modelo de 4B se distribuye fuera del MSI por el límite de archivo del formato CAB. El portátil puede incluirlo, con sus licencias y manifiesto.")
    table(doc, ("Situación", "Acción"), [("Faltan motores", "Preparar componentes o seleccionar su carpeta en Configuración."), ("No hay micrófono compatible", "Importar un WAV o continuar con texto manual."), ("Falla una transcripción", "Conservar el audio y reintentar; revisar la narración."), ("Qwen no carga o tarda demasiado", "Revisar memoria y componentes; continuar sin IA."), ("Sesión bloqueada", "Ingresar nuevamente. Se recupera lo guardado."), ("No abre un PDF confirmado", "Intentar regenerar desde su versión y revisar permisos de archivos.")], [2.5, 3.85])
    p(doc, "Los respaldos incluyen SQLite, documentos y audios; no incluyen modelos ni están cifrados. Mantener copias protegidas y comprobar restauración. No subir bases, audios o reportes privados a Git.")
    save(doc, "Voxora Guia de ejecucion.docx")


def source_guide():
    doc = new("Guía del código fuente de Voxora", "Arquitectura y mantenimiento")
    p(doc, "El código completo está en https://github.com/JuanGTito/voxora y se entrega también como ZIP de fuente. Este documento explica su organización y sus reglas; no sustituye los archivos Java. El primer commit publicado del núcleo es 5e19d64 y la versión documentada es 0.2.0.")
    table(doc, ("Módulo", "Responsabilidad principal"), [("bootstrap", "Composición, rutas, migraciones y bloqueo de instancia."), ("domain", "Usuarios, incidencias, estados, versiones y reglas comunes."), ("identity", "Argon2id, sesiones, actividad y autorización."), ("incidents", "Creación, asignación, atención, envío y revisión."), ("reports", "Puerto documental y adaptadores PDFBox y POI."), ("store", "Puerto transaccional y SQLite con migraciones."), ("settings", "Snapshot, respaldo y recuperación de restauraciones."), ("voice", "Audio, componentes verificados, STT y extracción local."), ("ui", "JavaFX, modelos de vista y diálogo de voz.")], [1.25, 5.1])
    p(doc, "La arquitectura es un monolito modular local. Los controladores no ejecutan SQL ni deciden por sí mismos permisos. No hay SOAP, backend de negocio compartido, app móvil ni sincronización por red.")

    heading(doc, "Dominio persistencia y autorización", True)
    p(doc, "Main prepara cachés y lanza JavaFX. AppContext impide dos procesos sobre la misma carpeta, abre SQLite y compone servicios. Repository es el puerto de aplicación; SqliteRepository coordina transacciones y mantiene claves foráneas activas.")
    table(doc, ("Acción", "Transición", "Actor"), [("Crear", "Nueva a PENDIENTE", "Supervisor"), ("Asignar", "PENDIENTE o ASIGNADO a ASIGNADO", "Supervisor"), ("Iniciar", "ASIGNADO a EN_PROCESO", "Técnico asignado"), ("Guardar o aplicar IA", "EN_PROCESO se mantiene", "Técnico asignado"), ("Confirmar envío", "EN_PROCESO a EN_REVISION", "Técnico asignado"), ("Devolver con motivo", "EN_REVISION a EN_PROCESO", "Supervisor"), ("Aprobar", "EN_REVISION a COMPLETADO", "Supervisor distinto del autor")], [1.35, 3.35, 1.65])
    p(doc, "Las credenciales usan Argon2id con sal aleatoria; las sesiones se validan en cada caso de uso. Los permisos se conservan como plantillas de rol. El técnico no puede consultar ni alterar trabajos ajenos aunque invoque el servicio directamente.")
    p(doc, "V001 define el núcleo y V002 incorpora voice_entries y el enlace desde report_versions. Cada migración conserva checksum. No editar una migración ya aplicada: añadir una nueva. La restauración admite esquemas anteriores compatibles y los actualiza.")

    heading(doc, "Voz propuestas y procedencia", True)
    p(doc, "AudioFiles normaliza WAV y registra PCM con un encabezado actualizado periódicamente. WhisperEngine invoca el motor local y conserva el original. VoiceService mantiene audio, texto corregido, hash de fuente, JSON y propuesta. Una versión aplicada enlaza voice_entry_id.")
    code(doc, '{\n  "actividades": [0],\n  "materiales": [0],\n  "pruebas": [],\n  "resultados": [1],\n  "observaciones": [2],\n  "hallazgos": []\n}')
    p(doc, "Los números son índices de oraciones en la narración. LlamaExtractor comprueba esquema, tipos, límites y referencias; reconstruye la propuesta copiando frases completas. Un índice fuera de rango o una categoría inventada se rechazan. Cambiar el texto invalida la propuesta previa.")
    p(doc, "La salvaguarda de negaciones aparta mediciones explícitamente no realizadas de la categoría de pruebas. Es conservadora y limitada; no acredita comprensión técnica completa ni reemplaza la revisión humana. El campo hallazgos de esta extracción no crea incidencias.")
    p(doc, "AiRuntime verifica componentes y binarios extraídos, no usa proxies para inferencia y limita el servidor a loopback con clave efímera. Los motores se cargan por trabajo y terminan después. La limpieza espera liberación de archivos temporales en Windows.")

    heading(doc, "Equipo pruebas y cambios futuros", True)
    table(doc, ("Integrante", "Referencia"), TEAM, [5.0, 1.35])
    p(doc, "Las declaraciones explícitas public llevan únicamente las iniciales LFTY, JGTM, SCGC, YARS y DMFY. No hay cabeceras por archivo ni etiquetas de equipo en el código. No se marcan parámetros, variables internas ni instrucciones ni se atribuyen contribuciones individuales.")
    p(doc, "AnnotateTeam retira las marcas antiguas y anota únicamente las declaraciones public. Usa javac para comparar el árbol sintáctico antes y después. Las dependencias y las migraciones históricas no se anotan.")
    p(doc, "La suite completa pasó 44 casos: 28 de negocio, 11 de voz, cuatro de interfaz y uno con motores reales. El caso real usa audio sintético y una corrección proporcionada por la prueba; no representa medición general de precisión ni prueba de micrófono humano.")
    p(doc, "Para continuar: añadir migraciones en lugar de modificarlas; mantener autorización en servicios; conservar versión exacta al enviar; verificar fallos de archivos y rollback; no permitir a la IA cerrar trabajos; probar permisos y recuperación junto con las funciones nuevas.")
    p(doc, "Pendientes explícitos: VM de 4 GB, captura real, ruido y acentos, instalación externa, anexos, hallazgos como flujo propio, preventivos, asignación automática y recuperación segura del único soporte. No se publican motores descargados, modelos, bases, audios ni configuraciones privadas del IDE.")
    save(doc, "Voxora Guia del codigo fuente.docx")


if __name__ == "__main__":
    user_manual()
    execution_guide()
    source_guide()
