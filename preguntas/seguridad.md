# Autenticación y permisos

`EducaFlowAuthResolver` y `EducaFlowAuthResolverRegistry` (resolución del usuario autenticado y cómo se encadena con el resolver de Axelor), `AuthSecurity` (permisos, condiciones y filtros de seguridad), lo que toca a auth en `AppModule`/`AppServletModule`, y la pantalla de login.

Cada pregunta es una sección `## <pregunta resumida>` añadida al final, con la plantilla de [`README.md`](README.md).

## Varios `Permission` sobre la misma entidad: ¿OR? ¿Por qué se comportan distinto al buscar filas que al pedir una fila concreta?

Se unen con OR, pero un `Permission` **sin condición** se trata distinto según quién pregunte: al pedir una fila concreta vale `true`, y al buscar filas se ignora.
La lógica es la original de Axelor: el fork solo cambió de dónde salen los `Permission` (`resolvePermissions`, `axelor-core/src/main/java/com/axelor/auth/AuthSecurity.java:200-207`, commit `3b387c2b4`).

**Paso 1, qué `Permission` se juntan (igual en los dos casos).**
`AuthResolver#resolve` suma en un único `Set` los del usuario, los de sus roles, los de su grupo y los de los roles del grupo (`axelor-core/src/main/java/com/axelor/auth/AuthResolver.java:98-123`).
No hay precedencia entre ellos aunque el javadoc diga «else» (`AuthResolver.java:87-91`): se hace `addAll` de todos.
De cada origen se queda con los que tienen el flag pedido (`hasAccess`, `AuthResolver.java:25-45`) y cuyo `object` es la clase exacta **o** `paquete.*` (`AuthResolver.java:66-79`): entran los dos, no gana el más específico.

**Paso 2A, una fila concreta: `AuthSecurity#isPermitted(type, model, ids)`** (`AuthSecurity.java:155-184`).
Se usa al abrir, escribir o borrar un registro (p. ej. `axelor-core/src/main/java/com/axelor/rpc/Resource.java:988`, `:1434`).
- Si algún `Permission` tiene `condition == null` y el flag, devuelve `true` sin mirar nada más (`AuthSecurity.java:167-172`).
- Si no, construye `OR(condiciones) AND id IN (ids)` con `getFilter` y exige que `count() == ids.length` (`AuthSecurity.java:178-183`).
- Es un OR de verdad: el incondicional equivale a `TRUE`.

**Paso 2B, buscar filas: `AuthSecurity#getFilter(READ, model)` sin ids** (`AuthSecurity.java:117-152`).
Lo usa `Resource#search` (grid, `/ws/rest/<FQN>/search`) (`Resource.java:436`).
- Recorre los `Permission` y solo añade al OR los que tienen condición; `getCondition` devuelve `null` para el incondicional y se salta (`AuthSecurity.java:82-89`, `:130-135`).
- Si no queda ninguna condición, devuelve `null` (sin filtro: todas las filas) (`AuthSecurity.java:137-139`), y `search` solo hace `check(CAN_READ, model)` (`Resource.java:443-445`).
- Si queda alguna, devuelve `Filter.or(condiciones)` (`AuthSecurity.java:141`): el incondicional **no aporta `TRUE` al OR**, se pierde.
- Si hay filtro, `search` le añade `OR parentFilter` (el del registro padre en un one-to-many) (`Resource.java:438-442`).

**Resultado.**

| Permisos que aplican        | Una fila (2A)                 | Búsqueda (2B)                       |
|-----------------------------|-------------------------------|-------------------------------------|
| solo incondicionales        | todas                         | todas (sin filtro)                  |
| solo condicionales          | OR de condiciones             | OR de condiciones                   |
| incondicional + condicional | todas (gana el incondicional) | **solo el OR de los condicionales** |

En el caso mixto el grid muestra menos filas de las que se pueden abrir por id.
No parece intencionado (sin verificar: no hay comentario ni test que lo justifique): «sin condición» solo equivale a «sin filtro» cuando no hay otro `Permission` condicional.

**Otros detalles.**
- `isPermitted` **sin ids** (p. ej. `check(CAN_WRITE, model)` en `Resource#updateMass`, `Resource.java:1393`, o `CREATE`) devuelve `true` en cuanto existe cualquier `Permission` con el flag, tenga condición o no (`AuthSecurity.java:174-176`): la condición no protege `create`.
- El admin se salta todo: `getUser()` devuelve `null` si `isAdmin` (`AuthSecurity.java:74-80`), y entonces `isPermitted` es `true` (`:156-159`) y `getFilter` es `null` (`:118-121`).
- `isPermitted` compara `getCondition() == null` (`AuthSecurity.java:169`) y `getCondition` trata el texto en blanco como sin condición (`:85`): una `condition=""` no hace el atajo de 2A, pero acaba dando el mismo resultado por el `id IN` (`:137-151`).

**Cómo evitar el caso mixto.**
Para una entidad y un flag, no generar a la vez un `Permission` incondicional y otros condicionales.
O se quedan solo con el incondicional (ya cubre a los demás), o el incondicional lleva una condición siempre verdadera para que entre en el OR de 2B.

**Referencias.**
- `axelor-core/src/main/java/com/axelor/auth/AuthSecurity.java` — `isPermitted`, `getFilter`, `getCondition`, `getUser`, `resolvePermissions`.
- `axelor-core/src/main/java/com/axelor/auth/AuthResolver.java` — `resolve` (suma de orígenes), `filterPermissions` (exacto + comodín), `hasAccess`.
- `axelor-core/src/main/java/com/axelor/rpc/Resource.java` — `search` (`:436-445`), `read` (`:988`), `updateMass` (`:1393`), `remove` (`:1434`).

## ¿Tiene algún problema darle al `Permission` incondicional una condición siempre verdadera (`1 = 1`) para que no se pierda en la búsqueda?

Sí: arregla la búsqueda, pero rompe la comprobación de una fila cuando el id es `null`, y añade una consulta `COUNT` por cada comprobación.

**Lo que sí arregla.**
- `getFilter` sin ids mete `(1 = 1)` en el OR, porque `JPQLFilter#getQuery` envuelve cada condición entre paréntesis (`axelor-core/src/main/java/com/axelor/rpc/filter/JPQLFilter.java:41-43`) y `Filter.or` las une (`AuthSecurity.java:141`).
- Sin `?` no gasta parámetros: `Filter#build` solo renumera los `?` que encuentra (`axelor-core/src/main/java/com/axelor/rpc/filter/Filter.java:24-36`) y con `conditionParams` vacío la lista de argumentos queda vacía (`AuthSecurity.java:35-49`).
- Que Hibernate acepte `1 = 1` en el `WHERE`: sin verificar en este repo.

**Problema 1: el id `null` (el importante).**
- Al perder el atajo (`AuthSecurity.java:167-172`), `isPermitted` con ids pasa a `getFilter(type, model, ids)` y exige `count() == ids.length` (`AuthSecurity.java:178-183`).
- Si el id es `null`, `ids` es `[null]`, de longitud 1 (`AuthSecurity.java:174`), pero `getFilter` no añade `id IN` porque comprueba `ids[0] != null` (`AuthSecurity.java:144-146`).
  El filtro queda en `(1 = 1)` a secas, cuenta **todas** las filas de la tabla, y solo da `true` si la tabla tiene exactamente una.
- Llamadas que pasan el id de un bean que puede no estar guardado:
  - `ActionResponse#setValue` → `toPermitted` → `isPermitted(CAN_READ, clase, model.getId())` (`axelor-core/src/main/java/com/axelor/rpc/ActionResponse.java:378-386`, `:396-415`).
    Un controlador que hace `response.setValue("campo", entidadNueva)` recibe un mapa compacto (`id`, `$version`, `namecolumn`) en lugar de la entidad.
  - `Resource#filterPermitted` con campos con punto (`Resource.java:1700-1735`, el `check` en `:1728`), `Resource#mergeRelated` (`Resource.java:1133`), el padre del contexto en `Resource#getParentFilter` (`Resource.java:403-406`) y `DMSFileRepository` (`axelor-core/src/main/java/com/axelor/dms/db/repo/DMSFileRepository.java:448`).
- **Este fallo ya existe hoy para cualquier `Permission` con condición**: con `[null]` el filtro es `OR(condiciones)` sin `id IN`, y cuenta todas las filas que cumplen la condición.
  `1 = 1` solo lo extiende a las entidades que hoy son incondicionales.

**Problema 2: rendimiento.**
Sin atajo, cada `isPermitted` con id lanza un `SELECT COUNT` (`AuthSecurity.java:183`), incluido el `check` por cada campo con punto de cada fila en `filterPermitted` (`Resource.java:1728`).

**Alternativas.**
- En el generador: no emitir a la vez, para una entidad y un flag, un `Permission` incondicional y otros condicionales (el incondicional ya cubre a los demás).
- En el fork, `AuthSecurity#getFilter`: si algún `Permission` aplicable no tiene condición, no añadir el OR de condiciones (devolver solo el `id IN`, o `null` sin ids).
  Así 2A y 2B quedan coherentes sin trucos.
- Para el id `null`, en el fork, `AuthSecurity#isPermitted`: quitar los `null` de `ids` y, si no queda ninguno, tratarlo como «sin ids».

**Referencias.**
- `axelor-core/src/main/java/com/axelor/auth/AuthSecurity.java` — atajo del incondicional, `getFilter` con `ids[0] != null`, `count() == ids.length`.
- `axelor-core/src/main/java/com/axelor/rpc/filter/JPQLFilter.java` — paréntesis de cada condición.
- `axelor-core/src/main/java/com/axelor/rpc/filter/Filter.java` — renumerado de `?` en `build`.
- `axelor-core/src/main/java/com/axelor/rpc/ActionResponse.java` — `setValue` → `toPermitted` con `model.getId()`.
- `axelor-core/src/main/java/com/axelor/rpc/Resource.java` — `filterPermitted`, `mergeRelated`, `getParentFilter`.
- `axelor-core/src/main/java/com/axelor/dms/db/repo/DMSFileRepository.java` — `isPermitted(CREATE, DMSFile, file.getId())`.

## ¿Cómo evita Axelor Open Suite (AOS) que se descargue cualquier `MetaFile` por su id?

No lo evita: AOS concede lectura sobre **todos** los `MetaFile` sin condición, así que cualquier usuario con uno de sus roles base puede descargar cualquier fichero por su id.
Los roles de `axelor-base` llevan `perm.meta.r` sobre `com.axelor.meta.db.*` con solo `can_read` y sin `condition` para «Base Manager», «Base User» y «Base Read» (`axelor-open-suite/axelor-base/src/main/resources/apps/roles/base_permission.csv:431-433`).
El data-init da `perm.meta.all` sobre `com.axelor.meta.db.*` al rol «Admin» (`axelor-base/src/main/resources/data-init/input/auth_permission.csv:4`), y los datos de demo añaden `perm.meta.file.r` (`axelor-base/src/main/resources/apps/demo-data/en/auth_permission.csv:41`) y `perm.mobile.MetaFile.r` (`axelor-mobile-settings/src/main/resources/apps/demo-data/en/auth_permission.csv:12`), también sin condición.
Con eso, en la descarga de AOP el `CAN_READ` sobre `MetaFile` siempre se cumple y la comprobación del creador o del padre no llega a importar (`axelor-web/src/main/java/com/axelor/web/service/RestService.java:493-500`).
En el código Java de AOS no hay nada que sustituya o envuelva esa descarga: buscar `extends RestService` o comprobaciones de `CAN_READ` sobre `MetaFile` no da resultados (comprobado con grep sobre la copia local, commit `a8b3417541`).

**Referencias.**
- `axelor-open-suite/axelor-base/src/main/resources/apps/roles/base_permission.csv` — `perm.meta.r` para los roles base.
- `axelor-open-suite/axelor-base/src/main/resources/data-init/input/auth_permission.csv` — `perm.meta.all` para Admin.
- `axelor-open-suite/axelor-base/src/main/resources/apps/demo-data/en/auth_permission.csv` — `perm.meta.file.r`.
- `axelor-open-suite/axelor-mobile-settings/src/main/resources/apps/demo-data/en/auth_permission.csv` — `perm.mobile.MetaFile.r`.
- `axelor-web/src/main/java/com/axelor/web/service/RestService.java` — `download`.

## ¿Admite comodines el `object` de un `Permission`? ¿Cuáles y cómo se combinan con el exacto?

Sí, pero solo uno: `<paquete inmediato>.*`.
`AuthResolver#filterPermissions` acepta un `Permission` si su `object` es igual al nombre pedido (`axelor-core/src/main/java/com/axelor/auth/AuthResolver.java:67-71`) o igual a `object.substring(0, object.lastIndexOf('.')) + ".*"` (`AuthResolver.java:74-79`).
- Es una comparación de cadenas, no un patrón: `com.axelor.meta.db.*` cubre `com.axelor.meta.db.MetaFile`, pero `com.axelor.*` **no** (no sube por los paquetes padre) y un `*` a secas no cubre nada.
- No hay especificidad: el exacto y el comodín se **suman** en el mismo `Set` (`AuthResolver.java:61`, `:69`, `:77`), y luego se combinan con OR como cualquier otro `Permission` (ver la sección anterior).
- Si el nombre pedido no tiene ningún `.`, `lastIndexOf` es `-1` y `substring(0, -1)` lanza `StringIndexOutOfBoundsException` (`AuthResolver.java:74`); con entidades no pasa porque siempre se pide el FQN.
- `AuthResolver` es `final class` sin `public` (`AuthResolver.java:16`): solo se puede usar desde `com.axelor.auth` (lo instancia `AuthSecurity`).
- `MetaPermissions` (permisos por campo) no admite comodines: compara con `equals` (`axelor-core/src/main/java/com/axelor/meta/MetaPermissions.java:37`).

**Referencias.**
- `axelor-core/src/main/java/com/axelor/auth/AuthResolver.java` — `filterPermissions` (exacto + `paquete.*`, suma), visibilidad de la clase.
- `axelor-core/src/main/java/com/axelor/meta/MetaPermissions.java` — permisos de campo sin comodín.

## ¿Puede la `condition` de un `Permission` llamar a un método Java por cada fila?

No.
La `condition` es un fragmento JPQL que se envuelve tal cual en un `JPQLFilter` (`axelor-core/src/main/java/com/axelor/auth/AuthSecurity.java:49`), cuyo `getQuery` lo devuelve entre paréntesis para meterlo en el `WHERE` (`axelor-core/src/main/java/com/axelor/rpc/filter/JPQLFilter.java:41-42`): lo evalúa la base de datos, no la JVM.
Lo único que pasa por Java/Groovy son los `conditionParams`: cada uno se evalúa **una vez por usuario y petición**, con solo `__user__` en el binding, y su valor se pasa como parámetro `?N` (`AuthSecurity.java:35-46`, `:57`).
No hay binding de la fila, así que un parámetro no puede depender de ella.

Alternativas:
- Calcular en Java **antes** de la consulta lo que dependa del usuario (p. ej. una lista de ids o de centros) en un `conditionParams` y usar `self.x IN (?1)` en la `condition`.
- Si de verdad hace falta lógica por fila, pasarla a la BD: una función SQL (PL/pgSQL) llamada desde JPQL con `function('nombre', self.campo, ?1)` (sintaxis estándar de JPA 2.1, la traduce Hibernate; sin verificar en este repo), o un campo persistido/desnormalizado que se mantenga al guardar.

**Referencias.**
- `axelor-core/src/main/java/com/axelor/auth/AuthSecurity.java` — clase `Condition`: evaluación de `conditionParams` con Groovy y `__user__`, construcción del `JPQLFilter`.
- `axelor-core/src/main/java/com/axelor/rpc/filter/JPQLFilter.java` — la condición se inserta como JPQL literal.

## ¿Puede la `condition` indexar un `Map` de `__user__` con un campo de la fila (`'DIRECTOR' in __user__.mapa[self.tipoExpediente.code]`)?

No.
`__user__` llega a la consulta como un parámetro `?N` ya evaluado (`AuthSecurity.java:39-44`, `:49`), y JPQL no sabe indexar un `Map` Java con una columna de la fila: la fila solo existe en la BD.
Además JPA no puede persistir un `Map<String, List<X>>` (el valor de un mapa no puede ser una colección), así que tampoco se puede navegar como asociación con `KEY()`/`VALUE()`.

Dos salidas:
- Precalcular en el `conditionParams` la lista de claves que cumplen y usar `self.tipoExpediente.code IN (?1)`.
  Ojo: `conditionParams` se trocea por comas (`AuthSecurity.java:38`), así que la expresión Groovy **MUST NOT** llevar comas (`findAll { it.value.contains('DIRECTOR') }*.key`, no `findAll { k, v -> … }`).
  Con lista vacía, `IN ()` depende de Hibernate (sin verificar).
- Guardar la relación en una entidad (usuario, tipo de expediente, perfil) y filtrar con `EXISTS (SELECT … WHERE … = ?1 AND … = self.tipoExpediente)` con `conditionParams = __user__`.

**Referencias.**
- `axelor-core/src/main/java/com/axelor/auth/AuthSecurity.java` — clase `Condition`: `split(",")` de los parámetros, evaluación Groovy, `JPQLFilter`.
