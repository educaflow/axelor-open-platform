/*export   function insertFromHTML(selector: HTMLElement, url: string, position: "start" | "end" = "end") {
  fetch(url)
    .then(res => res.text())
    .then(text => {
      console.log("Cargando contenido desde:", url);
      console.log("Contenido bruto:\n", text);

      const parser = new DOMParser();
      const doc = parser.parseFromString(text, "text/html");

      // Como el contenido puede tener <link>, <script>, <style> que irían en head
      // o cualquier etiqueta en body, recogemos todos los hijos de head y body.
      // Pero en general, los includes deben tener contenido adecuado al destino.

      // Para insertarlo donde toca, usamos el selector que pasamos y posición
      const container = selector;

      if (!container) {
        console.error("Selector no encontrado");
        return;
      }

      // Aquí combinamos head + body children para insertar TODO
      const nodesToInsert = [
        ...Array.from(doc.head.children),
        ...Array.from(doc.body.children),
      ];

      // Si insertamos al principio, recorremos en orden inverso
      const orderedNodes = position === "start" ? [...nodesToInsert].reverse() : nodesToInsert;


      if (orderedNodes.length === 0) {
        console.warn(`No hay nodos para insertar en ${url}`);
        return;
      }

      orderedNodes.forEach(node => {
        console.log(`Insertando <${node.tagName.toLowerCase()}> desde ${url}`);

        if (node.tagName.toLowerCase() === "script") {
          const script = document.createElement("script");

          // Copiamos atributos
          for (const attr of node.attributes) {
            script.setAttribute(attr.name, attr.value);
          }

          // Si es script en línea (no tiene src)
          if (!script.src) {
            script.textContent = node.textContent;
          }

          // Insertar y ejecutar
          if (position === "start") {
            container.insertBefore(script, container.firstChild);
          } else {
            container.appendChild(script);
          }
        } else {
          // Clonamos y añadimos otros nodos normalmente
          const clone = node.cloneNode(true);
          if (position === "start") {
            container.insertBefore(clone, container.firstChild);
          } else {
            container.appendChild(clone);
          }
        }
      });

    })
    .catch(err => {
      console.error(`Fallo al cargar ${url}:`, err);
    });
}*/

export async function insertFromHTML(
  selector: HTMLElement,
  url: string,
  position: "start" | "end" = "end"
): Promise<void> {
  try {
    const response = await fetch(url);
    const htmlText = await response.text();

    console.log("Cargando contenido desde:", url);

    const parser = new DOMParser();
    const doc = parser.parseFromString(htmlText, "text/html");

    if (!selector) {
      console.error("Selector no encontrado");
      return;
    }

    const nodesToInsert = [
      ...Array.from(doc.head.children),
      ...Array.from(doc.body.children),
    ];

    if (nodesToInsert.length === 0) {
      console.warn(`No hay nodos para insertar en ${url}`);
      return;
    }

    const orderedNodes = position === "start" ? [...nodesToInsert].reverse() : nodesToInsert;

    for (const node of orderedNodes) {
      insertNode(selector, node, position, url);
    }
  } catch (err) {
    console.error(`Fallo al cargar ${url}:`, err);
  }
}

// Función auxiliar para insertar nodos, según el tipo
function insertNode(
  container: HTMLElement,
  node: Element,
  position: "start" | "end",
  url: string
) {
  console.log(`Insertando <${node.tagName.toLowerCase()}> desde ${url}`);

  if (node.tagName.toLowerCase() === "script") {
    const script = document.createElement("script");

    // Copiar atributos
    Array.from(node.attributes).forEach(attr =>
      script.setAttribute(attr.name, attr.value)
    );

    // Si es script en línea
    if (!script.src) {
      script.textContent = node.textContent;
    }

    appendToContainer(container, script, position);
  } else {
    const clone = node.cloneNode(true);
    appendToContainer(container, clone as HTMLElement, position);
  }
}

// Función auxiliar para insertar nodo en el contenedor
function appendToContainer(
  container: HTMLElement,
  node: HTMLElement,
  position: "start" | "end"
) {
  if (position === "start") {
    container.insertBefore(node, container.firstChild);
  } else {
    container.appendChild(node);
  }
}
