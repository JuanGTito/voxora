# Referencias del equipo en el código

| Integrante | Iniciales |
|---|---|
| Luis Fernando Ticona Yunganina | LFTY |
| Juan Gedeon Tito Moya | JGTM |
| Smith Clever Givera Coila | SCGC |
| Yuri Andrea Ramos Suca | YARS |
| Dimas Mijael Flores Yerba | DMFY |

Las declaraciones explícitas `public` de Java (clases, interfaces, registros, constructores, métodos y campos) llevan únicamente `/* LFTY · JGTM · SCGC · YARS · DMFY */`. No se usa la palabra «Equipo» en esos comentarios ni se agrega una cabecera por archivo. No se marcan parámetros, variables internas ni instrucciones. Los nombres del código conservan su significado.

Las iniciales son una referencia colectiva; no atribuyen aportes individuales. `scripts/AnnotateTeam.java` utiliza el analizador del JDK para localizar las declaraciones públicas y comprobar que el árbol sintáctico antes y después sea idéntico. Retira las marcas antiguas y `scripts/annotate-team.ps1` aplica el formato Java. No modifica nombres, reglas, permisos, dependencias ni migraciones SQL.