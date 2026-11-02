from pathlib import Path
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'documentacion'/'Voxora Manual de instalacion APA.docx'
doc=Document(); sec=doc.sections[0]
sec.page_width=Inches(8.5); sec.page_height=Inches(11)
sec.top_margin=sec.bottom_margin=sec.left_margin=sec.right_margin=Inches(1)
sec.header_distance=Inches(.5)
for name in ['Normal','Title','Heading 1','Heading 2','TOC 1','TOC Heading','Caption']:
 try: st=doc.styles[name]
 except KeyError: continue
 st.font.name='Times New Roman'; st.font.size=Pt(12); st.font.color.rgb=RGBColor(0,0,0)
 for node in list(st.element.xpath('.//w:pBdr')): node.getparent().remove(node)
 fonts=st.element.get_or_add_rPr().get_or_add_rFonts()
 for attr in list(fonts.attrib):
  if 'theme' in attr.lower(): del fonts.attrib[attr]
 for attr in ['ascii','hAnsi','eastAsia','cs']: fonts.set(qn('w:'+attr),'Times New Roman')
 st.paragraph_format.line_spacing=2
 st.paragraph_format.space_before=Pt(0); st.paragraph_format.space_after=Pt(0)
 st.paragraph_format.first_line_indent=Inches(0)
 if name in ['Title','Heading 1','TOC Heading']:
  st.font.bold=True; st.paragraph_format.alignment=WD_ALIGN_PARAGRAPH.CENTER
 elif name=='Heading 2': st.font.bold=True
n=doc.styles['Normal']; n.paragraph_format.first_line_indent=Inches(.5)
header=sec.header.paragraphs[0]; header.alignment=WD_ALIGN_PARAGRAPH.RIGHT
fld=OxmlElement('w:fldSimple'); fld.set(qn('w:instr'),'PAGE'); header._p.append(fld)
settings=doc.settings.element; update=OxmlElement('w:updateFields'); update.set(qn('w:val'),'true'); settings.append(update)
def p(text='',bold=False,indent=True):
 x=doc.add_paragraph(); x.paragraph_format.first_line_indent=Inches(.5 if indent else 0)
 x.add_run(text).bold=bold; return x
def h(text): doc.add_heading(text,1)
def page(title): doc.add_page_break(); h(title)
def step(n,text):
 x=p(f'Paso {n}. {text}',indent=False)
def code(text):
 x=p(indent=False); x.paragraph_format.line_spacing=1; x.paragraph_format.space_before=Pt(6); x.paragraph_format.space_after=Pt(6)
 r=x.add_run(text); r.font.name='Consolas'; r.font.size=Pt(10)
def result(text): p('Resultado esperado. '+text,bold=True,indent=False)
# 1
for _ in range(3): p('')
x=doc.add_paragraph('Manual de instalación de Voxora',style='Title')
x=doc.add_paragraph('Instalación en Windows desde el EXE y preparación desde GitHub'); x.alignment=WD_ALIGN_PARAGRAPH.CENTER; x.paragraph_format.first_line_indent=Inches(0)
p('')
for name in ['Luis Fernando Ticona Yunganina','Juan Gedeon Tito Moya','Smith Clever Givera Coila','Yuri Andrea Ramos Suca','Dimas Mijael Flores Yerba']:
 x=p(name,indent=False); x.alignment=WD_ALIGN_PARAGRAPH.CENTER
p('')
for text in ['Versión 0.2.0','1 de noviembre de 2026']:
 x=p(text,indent=False); x.alignment=WD_ALIGN_PARAGRAPH.CENTER
# 2
doc.add_page_break(); doc.add_paragraph('Índice', style='Title')
x=p(indent=False)
f=OxmlElement('w:fldSimple'); f.set(qn('w:instr'),'TOC \\o "1-1" \\h \\z \\u'); x._p.append(f)
p('')
p('Para instalar y utilizar Voxora, siga las secciones de requisitos, instalación y primer inicio. La preparación desde GitHub y la generación del instalador están dirigidas a quienes desarrollan el programa.')
p('Los números del índice corresponden a esta edición. Si modifica el documento en Word, actualice el índice completo antes de imprimirlo.')
# 3
page('Alcance y requisitos')
p('Voxora permite registrar incidencias y elaborar reportes técnicos mediante texto o voz local. Este manual explica cómo instalar la entrega 0.2.0 y comprobar su primer funcionamiento. El EXE completo incluye Java, Whisper Tiny, llama.cpp y Qwen3 4B Q4_K_M; el usuario no necesita instalar Python ni .NET.')
p('El código fuente y el instalador son entregas diferentes. Descargar el ZIP de GitHub proporciona archivos para desarrollo; no instala automáticamente la aplicación (Ticona Yunganina et al., 2026).')
p('Tabla 1',bold=True,indent=False)
x=p('Requisitos de la entrega',indent=False); x.runs[0].italic=True
t=doc.add_table(rows=1, cols=2); t.autofit=False; t.columns[0].width=Inches(1.6); t.columns[1].width=Inches(4.9)
for c,txt in zip(t.rows[0].cells,['Elemento','Condición']): c.text=txt
for a,b in [('Sistema','Windows 10 u 11 de 64 bits con arquitectura x64.'),('Instalación EXE','Reserve al menos 6 GB libres, además del espacio para audios y documentos. El EXE ocupa aproximadamente 2,73 GB.'),('Desarrollo','JDK 21; conexión para preparar dependencias y modelos. Se recomiendan 15 GB libres para empaquetar.'),('Memoria y voz','El rendimiento de Qwen3 4B con solo 4 GB de RAM sigue pendiente de validación.')]:
 cells=t.add_row().cells; cells[0].text=a; cells[1].text=b
for ri,row in enumerate(t.rows):
 for ci,cell in enumerate(row.cells):
  cell.width=Inches(1.6 if ci==0 else 4.9)
  tcPr=cell._tc.get_or_add_tcPr(); borders=OxmlElement('w:tcBorders')
  for edge in ['top','left','bottom','right']:
   e=OxmlElement('w:'+edge); e.set(qn('w:val'),'single' if ((ri==0 and edge in ['top','bottom']) or (ri==len(t.rows)-1 and edge=='bottom')) else 'nil'); e.set(qn('w:sz'),'4'); e.set(qn('w:color'),'D9D9D9'); borders.append(e)
  tcPr.append(borders)
  if ri==0:
   sh=OxmlElement('w:shd'); sh.set(qn('w:fill'),'FFFFFF'); tcPr.append(sh)
  for par in cell.paragraphs:
   par.paragraph_format.first_line_indent=Inches(0); par.paragraph_format.line_spacing=1
   par.paragraph_format.space_before=Pt(5); par.paragraph_format.space_after=Pt(5)
   for r in par.runs: r.font.size=Pt(11); r.bold=(ri==0)
p('Nota. Las cifras de almacenamiento son una reserva práctica. No representan una medición de rendimiento en todos los equipos.',indent=False)
# 4
page('Instalación desde el EXE')
step(1,'Obtenga del responsable de la entrega el archivo Voxora-Setup-0.2.0-windows-x64.exe y su archivo .sha256. Guárdelos en Descargas. El instalador todavía no está publicado como descarga en el repositorio.')
step(2,'Si ya utiliza Voxora, haga un respaldo desde Configuración y cierre el programa antes de actualizar.')
step(3,'Abra el EXE con doble clic. Esta compilación no tiene firma digital de editor; si Windows muestra una advertencia, compruebe el origen con el responsable antes de continuar.')
step(4,'En el asistente en español, lea la bienvenida y la información. Pulse Siguiente para avanzar. Puede volver con Atrás.')
step(5,'Revise la carpeta de destino. Puede conservar %LOCALAPPDATA%\\Programs\\Voxora o elegir otra carpeta con permisos de escritura. Confirme la carpeta del menú Inicio.')
step(6,'Seleccione si desea un acceso directo en el escritorio. Revise el resumen y pulse Instalar. Espere la copia completa de los modelos, sin cerrar el asistente.')
step(7,'Pulse Finalizar. Puede marcar Abrir Voxora o ejecutarlo después desde Inicio. No necesita introducir comandos ni descargar modelos por separado.')
result('Voxora aparece en Inicio y abre la preparación inicial o el acceso a sus cuentas existentes.')
# 5
page('Primer inicio y comprobación de voz')
x=p('',indent=False); x.paragraph_format.line_spacing=1
step(1,'En una instalación nueva, complete Usuario, Contraseña y Confirmación. La contraseña requiere al menos 10 caracteres. Pulse Crear instalación y conserve su contraseña; no existe una predeterminada.')
p('Figura 1',bold=True,indent=False)
x=p('Creación de la primera cuenta',indent=False); x.runs[0].italic=True
x=doc.add_paragraph(); x.paragraph_format.first_line_indent=Inches(0); x.paragraph_format.line_spacing=1
x.add_run().add_picture(str(ROOT/'target/ui-evidence/first-run.png'),width=Inches(6.2))
x=p('Nota. Captura real de Voxora con campos vacíos, obtenida en un entorno de prueba.',indent=False); x.paragraph_format.line_spacing=1
step(2,'En una incidencia nueva o atención técnica, pulse Hablar, permita el acceso al micrófono si Windows lo solicita y narre una prueba breve. Pulse Detener, espere el procesamiento y revise el texto antes de confirmarlo. Si falla la voz, puede escribir manualmente.')
result('Puede acceder y revisar una narración sin crear registros definitivos hasta confirmar.')
# 6
page('Preparación desde GitHub')
x=p('',indent=False); x.paragraph_format.line_spacing=1
step(1,'Solo para desarrollo: instale un JDK 21 de 64 bits. En Eclipse Temurin seleccione JDK 21 y Windows x64. Su instalador permite configurar PATH y JAVA_HOME (Eclipse Adoptium, s. f.).')
step(2,'Abra una nueva consola PowerShell y compruebe ambas versiones. JAVA_HOME debe apuntar a la carpeta del JDK, no a su subcarpeta bin.')
code('java -version\njavac -version')
step(3,'Abra https://github.com/JuanGTito/voxora. Seleccione Code y Download ZIP; luego use Extraer todo. Alternativamente, si tiene Git instalado, clone el repositorio:')
code('git clone https://github.com/JuanGTito/voxora.git')
step(4,'Abra una consola en la carpeta que contiene pom.xml, mvnw.cmd y scripts. Para esta entrega deben existir scripts\\build-installer.ps1 y Crear instalador.cmd. Si faltan, solicite al responsable la entrega actualizada antes de seguir.')
step(5,'Opcionalmente, abra esa carpeta en IntelliJ IDEA, impórtela como proyecto Maven y seleccione JDK 21 como SDK del proyecto y de Maven. Maven Wrapper obtiene la versión configurada; no agregue bibliotecas manualmente.')
result('Código extraído por completo y JDK 21 disponible para compilar.')
# 7
page('Compilación y creación del instalador')
p('Ejecute los comandos desde la raíz del proyecto. La primera preparación descarga aproximadamente 2,6 GB de motores y modelos, verifica sus SHA-256 y conserva sus licencias. No suba esos archivos ni sus datos de trabajo a Git.')
step(1,'Prepare los modelos, compile con las pruebas existentes y abra una copia con datos aislados:')
code('.\\scripts\\prepare-ai.ps1\n.\\scripts\\build.ps1\n.\\scripts\\run.ps1 -DataDirectory ".\\.local-data\\prueba"')
step(2,'Para producir el EXE completo, cierre la prueba y abra Crear instalador.cmd con doble clic, o ejecute:')
code('.\\scripts\\build-installer.ps1')
p('El script ejecuta las pruebas, prepara los modelos, empaqueta Java mediante jpackage y compila el asistente con Inno Setup 7.1.1. Descarga y verifica el compilador oficial cuando falta. No se necesita un script ni un proyecto .NET.')
step(3,'Busque el EXE y su .sha256 en release\\<fecha-hora>\\installer. Pruebe esa entrega antes de distribuirla. No confunda el instalador con Voxora.exe, el acceso cotidiano al programa.')
p('Con dependencias y modelos ya preparados puede reconstruir sin conexión:')
code('.\\scripts\\build-installer.ps1 -Offline')
result('EXE completo generado, acompañado de su suma SHA-256.')
# 8
page('Datos mantenimiento y solución de problemas')
p('Los archivos del programa están en la carpeta elegida al instalar. Las cuentas, base de datos, documentos y audios se guardan por separado en %LOCALAPPDATA%\\Voxora. Para localizarla, pegue esa ruta en la barra de direcciones del Explorador.')
p('Antes de actualizar, cree un respaldo desde Configuración y cierre Voxora. Para desinstalar, abra Configuración de Windows, Aplicaciones, seleccione Voxora y pulse Desinstalar. La desinstalación conserva los datos; no elimina ni restablece las contraseñas.')
for title,text in [
 ('El programa no abre','Cierre cualquier otra instancia de Voxora del mismo perfil Windows. Vuelva a abrirlo desde Inicio; no ejecute el instalador cada vez que vaya a trabajar.'),
 ('La voz no funciona','Revise el micrófono, los permisos de Windows y la configuración de IA. En el EXE completo los modelos se detectan automáticamente. Conserve el texto y utilice la escritura manual si el motor falla.'),
 ('La compilación falla','Compruebe JDK 21, conexión y que la consola esté en la raíz del proyecto. Si PowerShell bloquea el script, use Crear instalador.cmd; en un equipo administrado consulte la política con su responsable.'),
 ('Olvidó la contraseña','Solicite al administrador autorizado el restablecimiento de la cuenta. No borre la base de datos para recuperar acceso; si perdió el único acceso de soporte, solicite una revisión técnica.')]:
 x=p(indent=False); x.add_run(title+'. ').bold=True; x.add_run(text)
p('La prueba local del instalador verificó 483 archivos, arranque y desinstalación conservando los datos. La validación de rendimiento con 4 GB y micrófono humano en el equipo de destino continúa pendiente.')
# 9
page('Referencias')
refs=[
 ('American Psychological Association. (s. f.). ', 'Paper format', '. APA Style. https://apastyle.apa.org/style-grammar-guidelines/paper-format/'),
 ('Eclipse Adoptium. (s. f.). ', 'Windows MSI installer packages', '. https://adoptium.net/installation/windows/'),
 ('', 'Manual de instalación y compilación desde GitHub y el ejecutable', '. (s. f.). [Manual de SistemaReportesApp proporcionado como referencia].'),
 ('Ticona Yunganina, L. F., Tito Moya, J. G., Givera Coila, S. C., Ramos Suca, Y. A., & Flores Yerba, D. M. (2026). ', 'Voxora', ' (versión 0.2.0) [Software de computadora y documentación de la entrega]. https://github.com/JuanGTito/voxora'),
]
for before,title,after in refs:
 x=p(indent=False); x.paragraph_format.left_indent=Inches(.5); x.paragraph_format.first_line_indent=Inches(-.5)
 x.add_run(before); x.add_run(title).italic=True; x.add_run(after)
p('')
p('El manual de SistemaReportesApp se consultó como modelo de organización de pasos y comprobaciones. Los requisitos, rutas y comandos de esta guía corresponden a Voxora.')
doc.core_properties.title='Manual de instalación de Voxora'
doc.core_properties.author='Luis Fernando Ticona Yunganina; Juan Gedeon Tito Moya; Smith Clever Givera Coila; Yuri Andrea Ramos Suca; Dimas Mijael Flores Yerba'
doc.core_properties.subject='Instalación Windows desde EXE y preparación del proyecto desde GitHub'
OUT.parent.mkdir(exist_ok=True); doc.save(OUT); print(OUT)
