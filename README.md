# Axelor Open Platform

[uri_axelor]: https://www.axelor.com
[uri_docs]: https://docs.axelor.com/adk/latest/
[uri_docs_install]: https://docs.axelor.com/adk/latest/getting-started/index.html
[uri_docs_tutorial]: https://docs.axelor.com/adk/latest/tutorial/step1.html
[uri_docs_guide]: https://docs.axelor.com/adk/latest/dev-guide/index.html
[uri_license]: https://www.gnu.org/licenses/agpl-3.0.html
[uri_license_image]: https://img.shields.io/badge/License-AGPL%20v3-blue.svg

[![License: AGPL v3][uri_license_image]][uri_license]

Axelor Open Platform is an open source Java framework to create modern business applications.

## Getting Started

```bash
$ axelor-publish-local.sh
```


Please follow the [installation guide][uri_docs_install] and
the [tutorial][uri_docs_tutorial] to get started quickly and read the
[developer's guide][uri_docs_guide] for more detailed documentation.

## Contributing

Please see the [CONTRIBUTING](CONTRIBUTING.md) documentation.

## Links

* [Axelor][uri_axelor]
* [Documentation][uri_docs]
* [License][uri_license]

## EduFlow Includes Filter

Este proyecto incorpora una funcionalidad para servir fragmentos HTML estáticos ubicados en la carpeta `/includes/` mediante un filtro servlet personalizado llamado `EduFlowIncludeFilter`.

### Archivos incluidos gestionados

El filtro intercepta las peticiones a estos archivos `.include.html` concretos y los sirve con el tipo MIME `text/html`, evitando pasar por la lógica habitual del controlador y mejorando rendimiento y modularidad.

- `head.start.include.html`
- `head.end.include.html`
- `body.start.include.html`
- `body.end.include.html`

### Uso en frontend

Para inyectar dinámicamente contenido en las secciones `<head>` y `<body>` del documento, se utiliza la utilidad `eduFlowHTMLInjector` escrita en TypeScript, que carga estos fragmentos mediante fetch y los inserta en el DOM.

---

### Clases añadidas

- `axelor-front/src/utils/eduFlowHTMLInjector.ts` — Utilidad frontend para insertar fragmentos HTML dinámicamente.
- `axelor-web/src/main/java/com/axelor/web/servlet/EduFlowIncludeFilter.java` — Filtro servlet backend para servir los archivos `.include.html` estáticos.

---
