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
