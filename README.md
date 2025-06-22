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
$ educaFlowInstall.sh
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

This project includes a custom servlet filter, `EduFlowIncludeFilter`, that serves static HTML snippets from the `/includes/` directory directly.

These snippets are used to dynamically inject HTML fragments like:

- `head.start.include.html`
- `head.end.include.html`
- `body.start.include.html`
- `body.end.include.html`

The filter intercepts requests to these specific `.include.html` files and serves them with `text/html` content type, bypassing usual controller logic to improve performance and modularity.

### How to use

Ensure your frontend uses these includes to inject dynamic content into the page head and body sections.

---

