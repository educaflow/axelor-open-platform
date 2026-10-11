# Vistas XML y widgets

Atributos nuevos del fork en `<form>`, `<grid>`, `<panel-related>`, `<button>`, `<dashboard>`, `<menuitem>`; los `<view-param>` (`show-toolbar-form`, `show-toolbar-grid`, `reload-grid`); cambios en cards, tree, dashlet, many-to-one, one-to-many, radio-select y binary; y cómo se propaga un atributo desde `object-views.xsd` y las clases de `com.axelor.meta.schema.views` hasta `meta.types.ts` y el widget.

Cada pregunta es una sección `## <pregunta resumida>` añadida al final, con la plantilla de [`README.md`](README.md).

## ¿Un elemento oculto con `showIf` ocupa columnas en el grid de un panel? (colSpan/colOffset)

No ocupa celda, pero **sí desplaza** a los que van detrás: las columnas se calculan una sola vez y en estático, sin mirar la visibilidad.
`computeLayout` recorre **todos** los items del panel y le da a cada uno `gridColumnStart = last + colOffset` y `gridColumnEnd = colStart + colSpan`, acumulando `last` también por los ocultos (`axelor-front/src/views/form/builder/form-layouts.tsx:44-60`).
Se memoiza solo sobre el `schema` (`form-layouts.tsx:127`), así que no se recalcula cuando cambia un `showIf`.
Si `colStart + colSpan` pasa de las columnas del panel, el item salta a la fila siguiente empezando en la columna 1 (`form-layouts.tsx:54-58`).
Un `colSpan` ausente vale el `itemSpan` del panel, y si tampoco hay, 6 (`form-layouts.tsx:18-19`, `:49`).
Un item oculto no se pinta: `FormWidget` devuelve `null` (`axelor-front/src/views/form/builder/form-widget.tsx:131-133`), su div `GridItem` queda vacío y `.gridItem:empty { display: none }` lo saca del grid (`form-layouts.module.scss`), así que no reserva celda.
Consecuencia: dos botones con condiciones opuestas (`showIf="x"` / `showIf="!x"`) seguidos consumen la suma de sus `colSpan` al calcular las columnas, aunque solo se vea uno; si se cuenta solo uno para calcular un `colOffset`, el botón de detrás se sale de las 12 columnas y salta de fila.
Para que la pareja ocupe un único hueco hay que meterla en un `<panel>` propio (sin marco) del ancho de la pareja, con los dos botones dentro a todo lo ancho: dentro de ese panel el oculto tampoco ocupa celda y el visible queda en la fila 1.
Otra forma, sin agrupar: darles a todos los botones alternativos **el mismo** `colOffset` y `colSpan` dentro de un panel propio.
Cada uno llena la fila (p.ej. `colOffset="3" colSpan="3"` en un panel de 6 columnas), así que el siguiente salta de fila con la misma columna (`form-layouts.tsx:51-58`); como el estilo solo fija la columna y no la fila, si el de delante está oculto el visible sube por auto-colocación a la fila libre y queda en el mismo sitio.
El número de columnas de un panel lo fija su atributo `cols` (por defecto 12, `form-layouts.tsx:17`, `axelor-core/src/main/resources/object-views.xsd:592`): con `colSpan="6" cols="6"` un `colSpan` dentro del panel mide lo mismo que en un panel de 12 columnas a todo lo ancho.

**Referencias.**
- `axelor-front/src/views/form/builder/form-layouts.tsx` — `computeLayout` (cálculo estático de columnas, salto de fila, `itemSpan` por defecto) y `GridLayout` (memo sobre el schema).
- `axelor-front/src/views/form/builder/form-layouts.module.scss` — `.gridItem:empty { display: none }`.
- `axelor-front/src/views/form/builder/form-widget.tsx` — `if (hidden) return null`.
- `axelor-core/src/main/resources/object-views.xsd` — atributo `cols` del panel (default 12).

## Cómo hacer que un `<panel>` empiece colapsado y el usuario lo pueda desplegar

Se usa `collapseIf="true"` en el `<panel>` (no hace falta `canCollapse`): el panel nace colapsado y el usuario lo despliega pulsando la cabecera.
`canCollapse` solo dice si el panel es plegable; si no se indica, vale `true` cuando hay `collapseIf` (`axelor-front/src/views/form/widgets/panel/panel.tsx:44`).
`collapseIf` se evalúa contra el registro y su resultado se guarda en el atributo `collapse` del widget (`axelor-front/src/views/form/builder/form-widget.tsx:495`), que es el estado inicial de `collapsed` (`panel.tsx:47-48`).
El usuario lo cambia con `toggleCollapse` (`panel.tsx:65-73`), que también actualiza el atributo `collapse` en `statesByName`.
El panel solo es plegable si tiene cabecera: `showTitle`/`showFrame` distintos de `false` y un `title` (`panel.tsx:75`, `panel.tsx:146`).
La expresión se reevalúa en cada cambio del registro (`form-widget.tsx:480-495`) y `handleCondition` solo escribe si el valor cambia (`form-widget.tsx:390`); si tras desplegar a mano el panel se vuelve a plegar al cambiar un campo está **sin verificar** en el navegador.
Al resetear el form (`form:reset-states`) vuelve al valor de `collapse` (`panel.tsx:53-62`).

**Referencias.**
- `axelor-front/src/views/form/widgets/panel/panel.tsx` — default de `canCollapse`, estado inicial, toggle y cabecera.
- `axelor-front/src/views/form/builder/form-widget.tsx` — evaluación de `collapseIf` → atributo `collapse`.
- `axelor-core/src/main/java/com/axelor/meta/schema/views/Panel.java` — atributos `canCollapse` y `collapseIf`.

## Qué hacen los atributos `sidebar` y `attached` de `<panel>`

**`sidebar="true"`** saca el panel de la columna principal y lo pinta en una columna lateral a la derecha.
Solo se mira en los hijos directos del `<form>`: `Layout` filtra `schema.items` por `x.sidebar` (`axelor-front/src/views/form/form.tsx:1656`).
La columna lateral mide `width: 25%` con `min-width: 300px` y la principal ocupa el resto (`axelor-front/src/views/form/form.module.scss:36-44`).
Si el contenedor mide menos de 768px no hay columna lateral: el primer panel `sidebar` va arriba, tras el `wkf-status`, y los demás al final, antes de los `panel-mail` (`form.tsx:1651`, `form.tsx:1664-1679`).
Si hay sidebar, el form no aplica la anchura `mid` por defecto (`form.tsx:1625`).
El XSD lo prohíbe en algunos tipos de panel que extienden `AbstractPanel` (`axelor-core/src/main/resources/object-views.xsd:2031`).

**`attached="true"`** según el XSD «pega el panel con el anterior» (`object-views.xsd:1768-1772`).
En el front React **no tiene ningún efecto**: solo aparece en la lista de atributos booleanos que `processWidgetAttrs` convierte de texto a booleano (`axelor-front/src/services/client/meta-utils.ts:114`) y en el tipo (`axelor-front/src/services/client/meta.types.ts:332`); ningún componente lo lee (búsqueda de `attached` en `axelor-front/src`: el resto de coincidencias son `data.attached` de exportaciones/informes en `executor.ts`).
Es herencia de la UI antigua (AngularJS), de ahí la suposición de que pegaba visualmente el panel al anterior (sin verificar).

**Referencias.**
- `axelor-front/src/views/form/form.tsx` — `Layout` (reparto main/side/small) y `useFormWidth`.
- `axelor-front/src/views/form/form.module.scss` — anchuras de `.main` y `.side`.
- `axelor-front/src/services/client/meta-utils.ts` — `attached`/`sidebar` como atributos booleanos.
- `axelor-core/src/main/resources/object-views.xsd` — documentación de `sidebar` y `attached`.
- `axelor-core/src/main/java/com/axelor/meta/schema/views/AbstractPanel.java` — atributo `attached`.
