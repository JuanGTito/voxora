# Referencias del equipo en el código

| Integrante | Iniciales |
|---|---|
| Luis Fernando Ticona Yunganina | LFTY |
| Juan Gedeon Tito Moya | JGTM |
| Smith Clever Givera Coila | SCGC |
| Yuri Andrea Ramos Suca | YARS |
| Dimas Mijael Flores Yerba | DMFY |

Por petición del equipo, las clases, interfaces, registros, métodos, declaraciones de variables y parámetros, condicionales y bucles Java incluyen el comentario `/* LFTY JGTM SCGC YARS DMFY */`. Es una referencia colectiva; no identifica aportes individuales que no se hayan especificado. Los identificadores conservan nombres descriptivos para que el código pueda leerse y mantenerse.

`scripts/AnnotateTeam.java` utiliza el analizador sintáctico del JDK 21 para colocar los comentarios. Comprueba que el árbol sintáctico antes y después sea idéntico; no modifica reglas, nombres ni permisos. `scripts/annotate-team.ps1` aplica el formato de Java antes y después. No se alteran las migraciones SQL ya publicadas, las dependencias de terceros ni los modelos.
