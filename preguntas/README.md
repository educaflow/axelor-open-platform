# Preguntas sobre AOP

Índice de las preguntas que se han hecho sobre Axelor Open Platform y sus respuestas, agrupadas por tema en un fichero cada una.
La regla de uso (cuándo y cómo se añade una pregunta) está en el [`CLAUDE.md`](../CLAUDE.md) de la raíz.

Cada pregunta es una sección `## <pregunta resumida>` al final del fichero de su tema.
Antes de investigar en el código de AOP, buscar aquí: si la pregunta ya está, se responde comprobando solo sus referencias.

Plantilla de cada sección:

```markdown
## <pregunta resumida>

<Respuesta: una frase por línea, cada afirmación con su referencia `ruta/Fichero.java:línea` o `Clase#método`.>

Flujo (si aplica):
1. `axelor-front/src/.../fichero.ts:NN` — qué ocurre en este paso.
2. `axelor-core/src/main/java/.../Clase.java:NN` — qué ocurre en este paso.

**Referencias.**
- `ruta/Fichero1.java` — qué demuestra.
- `ruta/fichero2.ts` — qué demuestra.
```

| Fichero | Tema |
|---------|------|
| [`acciones.md`](acciones.md) | Acciones built-in (`back`, `close`, `delete`, `save-modal`…), `executor.ts`, `ActionGroup`, señales, `executeJs`, cierre y validación de popups |
| [`vistas.md`](vistas.md) | Atributos nuevos en `<form>`/`<grid>`/`<panel-related>`/`<button>`/`<dashboard>`, `<view-param>`, cards, tree, dashlet, widgets, XSD → `meta.types.ts` |
| [`servicios.md`](servicios.md) | `ModelService`, factory, `AllowProperties`, `BusinessMessages`, validación maestro-detalle, `Resource`/`RestService`, excepciones |
| [`modelos.md`](modelos.md) | Generación desde `domains.xml` (`extra-code-model`, enums, `cascade`), `MetaFiles`/ficheros, data-init (`priority`, `DataLoader`, `XMLBinder`) |
| [`seguridad.md`](seguridad.md) | `EducaFlowAuthResolver`/`Registry`, `AuthSecurity`, auth en los módulos Guice, login |
| [`plataforma.md`](plataforma.md) | Build (`install.sh`, Gradle, Tomcat, `--contextPath`), config, tema, header/drawer, pestañas, about, inyección de HTML |

Si un tema nuevo no encaja en ninguno, se crea su fichero y se añade una fila aquí.
