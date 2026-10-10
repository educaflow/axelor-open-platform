# Modelos, generación de código y data-init

Generación de entidades desde `domains.xml` (`EntityGenerator`, `extra-imports-model`/`extra-code-model` en entidades y enums, atributo `cascade`, `getTitle()`/`getDescription()` en enums, XML de modelos en subdirectorios o junto al código Java), `AuditableModel`, `MetaFiles` y la subida/descarga de ficheros (`binary-eduFlow`), y la carga de datos iniciales (`DataLoader`, `XMLBinder`, varias carpetas `data-init`, atributo `priority`, `data-import.xsd`, datos de demo).

Cada pregunta es una sección `## <pregunta resumida>` añadida al final, con la plantilla de [`README.md`](README.md).

## ¿Puede JPA/Hibernate cachear automáticamente una tabla de solo lectura (caché de segundo nivel y de consultas)?

Sí: AOP activa la caché de segundo nivel **y** la de consultas de Hibernate siempre que `jakarta.persistence.sharedCache.mode` sea `ALL`, `ENABLE_SELECTIVE` o `DISABLE_SELECTIVE` (`axelor-core/src/main/java/com/axelor/db/internal/DBHelper.java:202-217`).
En ese caso `JpaModule#configureCache` pone `USE_SECOND_LEVEL_CACHE=true` y `USE_QUERY_CACHE=true` (`axelor-core/src/main/java/com/axelor/db/JpaModule.java:170-177`) y, sin otra configuración, usa JCache con Caffeine en memoria (`JpaModule.java:234-240`, `axelor-core/src/main/java/com/axelor/cache/CacheConfig.java:24`).
Con `ENABLE_SELECTIVE` solo se cachean las entidades marcadas: en el `domains.xml` es el atributo `cacheable="true"` de `<entity>`, que genera `@jakarta.persistence.Cacheable` (`axelor-tools/src/main/java/com/axelor/tools/code/entity/model/Entity.java:86,617-626`).
Para que el **resultado de una consulta** se cachee hay que pedirlo en cada consulta con `Query#cacheable()` (`axelor-core/src/main/java/com/axelor/db/Query.java:249-252`), que se pasa al `QueryBinder` al ejecutar (`Query.java:384,418`).
La caché de consultas guarda solo los ids; las entidades se leen de la caché de segundo nivel, así que la entidad también tiene que ser `cacheable` (comportamiento estándar de Hibernate, sin verificar en el código de AOP).
Hibernate invalida la región de consultas de una tabla cuando se escribe en ella a través de Hibernate (estándar de Hibernate, sin verificar en el código de AOP); lo escrito por JDBC directo no la invalida.

En `secretaria-virtual` ya está activa: `jakarta.persistence.sharedCache.mode = ENABLE_SELECTIVE` y `application.cache.provider = caffeine` (`secretaria-virtual/src/main/resources/axelor-config.properties:157,246`), aunque hoy ninguna entidad del proyecto declara `cacheable`.

**Referencias.**
- `axelor-core/src/main/java/com/axelor/db/internal/DBHelper.java` — `isCacheEnabled` y `getSharedCacheMode`.
- `axelor-core/src/main/java/com/axelor/db/JpaModule.java` — `configureCache` activa L2 y caché de consultas.
- `axelor-core/src/main/java/com/axelor/cache/CacheConfig.java` — proveedor JCache por defecto (Caffeine).
- `axelor-tools/src/main/java/com/axelor/tools/code/entity/model/Entity.java` — atributo `cacheable` → `@Cacheable`.
- `axelor-core/src/main/java/com/axelor/db/Query.java` — `cacheable()` por consulta.
- `secretaria-virtual/src/main/resources/axelor-config.properties` — configuración del proyecto.

## ¿De dónde sale el límite de caracteres de `Permission.condition`? (era 1024, ahora 4096)

En este fork se subió de 1024 a 4096.
Sale del atributo `max="4096"` del campo en el `domains.xml` del núcleo (`axelor-core/src/main/resources/domains/Permission.xml:21`), que además lo mapea a la columna `condition_value`.
El generador traduce `max` de un `<string>` **solo** a `@jakarta.validation.constraints.Size(max = <max>)` (`axelor-tools/src/main/java/com/axelor/tools/code/entity/model/Property.java:1074-1082`, en el bloque de anotaciones de validación).
`max` **no** pone `length` en `@Column`: el único `length` que genera es `Length.LONG32` para `large="true"` (`Property.java:1161-1163`).
Aun así la columna de BD es `varchar(<max>)` (comprobado con 1024 en `information_schema.columns` de `auth_permission.condition_value`; `condition_params`, sin `max`, es `varchar(255)`).
Esa longitud la pone Hibernate al generar el DDL (`db.default.ddl` → `hibernate.hbm2ddl.auto`, `axelor-core/src/main/java/com/axelor/db/JpaModule.java:155`): con la integración Bean Validation por defecto aplica `@Size(max)` como longitud de columna (comportamiento interno de Hibernate, `TypeSafeActivator`; **sin verificar** en el código de AOP, que no lo configura explícitamente).
Consecuencia: hay dos topes, `@Size` (solo salta si se ejecuta Bean Validation sobre la entidad; `DefaultModelService` no la ejecuta) y el `varchar(<max>)` de PostgreSQL, que siempre rechaza el insert/update.
Para ampliarlo se cambia `max` (o se pone `large="true"`) en `Permission.xml` del fork y, en las BD ya creadas, se altera la columna (`ALTER TABLE auth_permission ALTER COLUMN condition_value TYPE varchar(4096)`), porque `hbm2ddl=update` no amplía columnas existentes (**sin verificar**).

**Referencias.**
- `axelor-core/src/main/resources/domains/Permission.xml` — `max="4096"` y `column="condition_value"`.
- `axelor-tools/src/main/java/com/axelor/tools/code/entity/model/Property.java` — `max` → `@Size`; `large` → `@Column(length = LONG32)`.
- `axelor-core/src/main/java/com/axelor/db/JpaModule.java` — `db.<nombre>.ddl` → `hbm2ddl.auto`.

## Dado el id de un `MetaFile`, ¿se puede saber qué entidades y propiedades lo referencian?

AOP no guarda ninguna referencia inversa: `MetaFile` solo tiene `fileName`, `filePath`, `storeType`, `fileSize`, `fileType`, `description` y `sizeText` (`axelor-core/src/main/resources/domains/Meta.xml:251-270`).
Lo referencian las entidades con un `many-to-one`/`one-to-one` (columna FK) o un `many-to-many` (tabla de unión) a `MetaFile`, más `MetaAttachment` (`Meta.xml:272-277`) y `DMSFile`.
AOP tampoco lo busca así: la descarga recibe el padre del cliente (`parentId`/`parentModel`) y solo comprueba que ese padre es legible y que el fichero cuelga de **alguna** propiedad suya, sin distinguir cuál (`axelor-web/src/main/java/com/axelor/web/service/RestService.java:478-500` → `checkMetaFileParentPermission` 589 → `checkMetaFileParent` 618-647, con el `CAN_READ` del padre en 627 y el recorrido de propiedades en 635).
Para obtenerlo en el servidor hay que recorrerlo a mano: `JpaScanner.findModels()` (`axelor-core/src/main/java/com/axelor/db/JpaScanner.java:203`) da todas las entidades, `Mapper.of(clase).getProperties()` sus propiedades, y las que tienen `getTarget()` asignable a `MetaFile` son los candidatos (el mismo filtro que `RestService.java:635-640`); con eso se lanza una consulta por (entidad, propiedad).
Un mismo `MetaFile` **puede estar referenciado desde varias entidades a la vez** (en `secretaria-virtual` el PDF firmado de la solicitud es a la vez campo del expediente y `documentoOriginalFirmado` del registro de entrada; comprobado en BD), así que el resultado es un conjunto, no una única entidad.
Los campos de fichero en atributos JSON (`MetaJsonField`) no tienen FK y no salen por este camino (`RestService.java:636`, `checkMetaFileJsonProperty`).

**Referencias.**
- `axelor-core/src/main/resources/domains/Meta.xml` — `MetaFile` y `MetaAttachment`.
- `axelor-web/src/main/java/com/axelor/web/service/RestService.java` — `download`, `checkMetaFileParentPermission`, `checkMetaFileParent`.
- `axelor-core/src/main/java/com/axelor/db/JpaScanner.java` — `findModels`.
