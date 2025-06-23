export async function insertFromHTML(
  selector: HTMLElement,
  url: string,
  position: "start" | "end" = "end"
): Promise<void> {
  try {
    const response = await fetch(url);
    
    if (!response.ok) {
      return;
    }
    
    const htmlText = await response.text();

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