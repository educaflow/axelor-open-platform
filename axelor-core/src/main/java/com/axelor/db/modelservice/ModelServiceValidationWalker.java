/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.modelservice;

import com.axelor.common.StringUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.db.mapper.PropertyType;
import com.axelor.i18n.I18n;

import jakarta.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Valida en cascada los detalles (colecciones one-to-many de composición) de un maestro al
 * guardarlo o borrarlo por el endpoint REST genérico ({@code Resource.save} / {@code
 * Resource.remove}).
 *
 * <p>Motivo: el guardado de un maestro-detalle entra únicamente al {@link ModelService} del
 * maestro; los detalles se persisten por cascada JPA sin pasar por su propio servicio. Este walker
 * ejecuta los {@code validateInsert}/{@code validateUpdate}/{@code validateRemove} del servicio de
 * cada detalle (resuelto con {@link ModelServiceFactory}; {@link DefaultModelService} sin
 * validaciones si la entidad no tiene servicio propio), de forma recursiva a cualquier
 * profundidad. Solo validaciones: las acciones ({@code insert/update/remove}) y las reglas de
 * negocio del detalle siguen sin invocarse por esta vía.
 *
 * <p>Solo se desciende por colecciones de composición: {@code ONE_TO_MANY} con {@code
 * orphanRemoval} (es decir {@code !Property.isOrphan()}, ojo: {@code isOrphan()} es el negado de
 * {@code orphanRemoval}). Nunca por many-to-one/many-to-many, que son referencias.
 *
 * <p>La clasificación de cada hijo se hace desde el JSON del request, con la misma semántica que
 * aplica {@code JPA.edit} al persistir:
 *
 * <ul>
 *   <li>map con {@code id} nulo → nuevo → {@code validateInsert} (+ subárbol nuevo recursivo)
 *   <li>map con {@code id} y {@code version} no nula → editado → {@code validateUpdate(hijo,
 *       hijoOriginal)} (+ recursión con su sub-JSON)
 *   <li>map con {@code id} y {@code version} nula o ausente, o entrada numérica → referencia
 *       intacta → no se valida
 *   <li>presente en la colección del original pero ausente del JSON → borrado por orphan removal →
 *       {@code validateRemove} (+ subárbol borrado recursivo)
 *   <li>colección ausente del JSON → intacta → no se desciende
 * </ul>
 *
 * <p>Todos los mensajes se acumulan con un prefijo legible ("Cursos[FPB Informática] › …") y se
 * lanza una única excepción al final ({@link BusinessMessages#throwIfInvalid()}).
 */
public class ModelServiceValidationWalker {

  private final ModelServiceFactory modelServiceFactory;

  @Inject
  public ModelServiceValidationWalker(ModelServiceFactory modelServiceFactory) {
    this.modelServiceFactory = modelServiceFactory;
  }

  /**
   * Valida los detalles del árbol de un guardado. Llamar dentro de la transacción, entre {@code
   * JPA.edit} y {@code JPA.manage} (antes de {@code manage} los hijos nuevos conservan {@code id ==
   * null}).
   *
   * @param modelClass clase del maestro
   * @param json record del request, ya filtrado por {@code modelService.validate} (conserva {@code
   *     id}/{@code version})
   * @param bean árbol montado por {@code JPA.edit}
   * @param original snapshot detached del maestro con las colecciones de composición presentes en
   *     el JSON ya inicializadas; {@code null} si el maestro es nuevo
   */
  public void validateSaveTree(Class<? extends Model> modelClass, Map<String, Object> json, Model bean, Model original) {
    BusinessMessages messages = new BusinessMessages();
    walkSave(modelClass, json, bean, original, "", new HashSet<>(), messages);
    messages.throwIfInvalid();
  }

  /**
   * Valida con {@code validateRemove} todos los detalles (recursivo) de un maestro que se va a
   * borrar. Llamar dentro de la transacción, con el bean managed (colecciones lazy accesibles) y
   * antes de {@code modelService.remove}. No valida el maestro: eso lo hace su propio {@code
   * remove}.
   */
  public void validateRemoveTree(Model bean) {
    BusinessMessages messages = new BusinessMessages();
    walkRemove(EntityHelper.getEntityClass(bean), bean, "", new HashSet<>(), messages);
    messages.throwIfInvalid();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void walkSave(
      Class<?> klass,
      Map<String, Object> json,
      Model bean,
      Model original,
      String prefix,
      Set<Object> visited,
      BusinessMessages out) {

    if (json == null || bean == null) {
      return;
    }

    for (Property property : compositionProperties(klass)) {
      if (!json.containsKey(property.getName())) {
        continue; // colección no enviada por el cliente → intacta
      }

      final Class<? extends Model> target = (Class<? extends Model>) property.getTarget();
      final ModelService service = modelServiceFactory.resolve(target);
      final String collectionTitle = collectionTitle(property);

      final Object rawValue = json.get(property.getName());
      final Collection<?> jsonItems =
          rawValue instanceof Collection ? (Collection<?>) rawValue : List.of();
      final Collection<? extends Model> beanItems = toCollection(property.get(bean));
      final Collection<? extends Model> originalItems =
          original != null ? toCollection(property.get(original)) : List.of();

      final Map<Long, Model> beanById = indexById(beanItems);
      final Map<Long, Model> originalById = indexById(originalItems);

      // Ids presentes en el JSON en cualquier forma (map con id —con o sin version— o número).
      // CRITICAL: si faltara alguno, un hijo intacto se clasificaría como borrado.
      final Set<Long> jsonIds = collectIds(jsonItems);

      // 1) Nuevos: en el bean con id == null (antes de JPA.manage aún no tienen id).
      int index = 0;
      for (Model child : beanItems) {
        index++;
        if (child == null || child.getId() != null || !visited.add(child)) {
          continue;
        }
        String childPrefix = childPrefix(prefix, collectionTitle, target, child, index);
        collect((Optional<BusinessMessages>) service.validateInsert(child), childPrefix, out);
        walkNewChildren(target, child, childPrefix, visited, out);
      }

      // 2) Editados: maps del JSON con id > 0 y "version" NO nula (misma regla que JPA._edit:
      // `values.get("version") == null` → referencia intacta, no se muta ni se valida; la clave
      // puede estar presente con valor null y sigue siendo una referencia).
      for (Object item : jsonItems) {
        if (!(item instanceof Map)) {
          continue;
        }
        Map<String, Object> childJson = (Map<String, Object>) item;
        Long id = findId(childJson);
        if (id == null || id <= 0 || childJson.get("version") == null) {
          continue;
        }
        if (!visited.add(target.getName() + ":" + id)) {
          continue;
        }
        Model childBean = beanById.get(id);
        if (childBean == null) {
          childBean = JPA.find(target, id);
        }
        if (childBean == null) {
          continue;
        }
        Model childOriginal = originalById.get(id);
        String childPrefix = childPrefix(prefix, collectionTitle, target, childBean, 0);
        collect(
            (Optional<BusinessMessages>) service.validateUpdate(childBean, childOriginal),
            childPrefix,
            out);
        walkSave(target, childJson, childBean, childOriginal, childPrefix, visited, out);
      }

      // 3) Borrados: en la colección del original pero ausentes del JSON (orphan removal en flush).
      for (Model originalChild : originalItems) {
        if (originalChild == null
            || originalChild.getId() == null
            || jsonIds.contains(originalChild.getId())) {
          continue;
        }
        Model managed = JPA.find(target, originalChild.getId());
        Model victim = managed != null ? managed : originalChild;
        String childPrefix = childPrefix(prefix, collectionTitle, target, victim, 0);
        collect((Optional<BusinessMessages>) service.validateRemove(victim), childPrefix, out);
        walkRemove(target, victim, childPrefix, visited, out);
      }
    }
  }

  /** Subárbol 100% nuevo: todo hijo de composición se valida con {@code validateInsert}. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private void walkNewChildren(
      Class<?> klass, Model bean, String prefix, Set<Object> visited, BusinessMessages out) {

    for (Property property : compositionProperties(klass)) {
      final Class<? extends Model> target = (Class<? extends Model>) property.getTarget();
      final ModelService service = modelServiceFactory.resolve(target);
      final String collectionTitle = collectionTitle(property);

      int index = 0;
      for (Model child : toCollection(property.get(bean))) {
        index++;
        if (child == null || !visited.add(child)) {
          continue;
        }
        String childPrefix = childPrefix(prefix, collectionTitle, target, child, index);
        collect((Optional<BusinessMessages>) service.validateInsert(child), childPrefix, out);
        walkNewChildren(target, child, childPrefix, visited, out);
      }
    }
  }

  /**
   * Subárbol que va a desaparecer (orphan removal o borrado del maestro): {@code validateRemove}
   * recursivo sobre el estado persistente (bean managed dentro de la transacción).
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private void walkRemove(
      Class<?> klass, Model bean, String prefix, Set<Object> visited, BusinessMessages out) {

    Object visitedKey = bean.getId() != null ? "removed:" + klass.getName() + ":" + bean.getId() : bean;
    if (!visited.add(visitedKey)) {
      return;
    }

    for (Property property : compositionProperties(klass)) {
      final Class<? extends Model> target = (Class<? extends Model>) property.getTarget();
      final ModelService service = modelServiceFactory.resolve(target);
      final String collectionTitle = collectionTitle(property);

      int index = 0;
      for (Model child : toCollection(property.get(bean))) {
        index++;
        if (child == null) {
          continue;
        }
        String childPrefix = childPrefix(prefix, collectionTitle, target, child, index);
        collect((Optional<BusinessMessages>) service.validateRemove(child), childPrefix, out);
        walkRemove(target, child, childPrefix, visited, out);
      }
    }
  }

  /*************************************************************************************/
  /********************************      Helpers       ********************************/
  /*************************************************************************************/

  /** Colecciones de composición: one-to-many con orphanRemoval (isOrphan() es su negado). */
  private List<Property> compositionProperties(Class<?> klass) {
    List<Property> result = new java.util.ArrayList<>();
    for (Property property : Mapper.of(klass).getProperties()) {
      if (property.getType() == PropertyType.ONE_TO_MANY && !property.isOrphan()) {
        result.add(property);
      }
    }
    return result;
  }

  /** Fusiona los mensajes del hijo en el acumulador, prefijando el mensaje para que el diálogo
   * de error del maestro sea autoexplicativo. */
  private void collect(Optional<BusinessMessages> result, String prefix, BusinessMessages out) {
    result.ifPresent(
        messages ->
            messages.forEach(
                message ->
                    out.add(
                        new BusinessMessage(
                            message.getFieldName(),
                            prefix + message.getMessage(),
                            message.getLabel()))));
  }

  private String childPrefix(
      String prefix, String collectionTitle, Class<?> target, Model child, int index) {
    return prefix + collectionTitle + "[" + childLabel(target, child, index) + "] › ";
  }

  /** Etiqueta identificadora del hijo: su name-field, o "#id", o el índice en la colección. */
  private String childLabel(Class<?> target, Model child, int index) {
    if (child != null) {
      Property nameField = Mapper.of(target).getNameField();
      if (nameField != null) {
        Object value = nameField.get(child);
        if (value != null && StringUtils.notBlank(value.toString())) {
          return value.toString();
        }
      }
      if (child.getId() != null) {
        return "#" + child.getId();
      }
    }
    return "#" + index;
  }

  private String collectionTitle(Property property) {
    String title = property.getTitle();
    return StringUtils.notBlank(title) ? I18n.get(title) : property.getName();
  }

  @SuppressWarnings("unchecked")
  private Collection<? extends Model> toCollection(Object value) {
    return value instanceof Collection ? (Collection<? extends Model>) value : List.of();
  }

  private Map<Long, Model> indexById(Collection<? extends Model> items) {
    Map<Long, Model> result = new HashMap<>();
    for (Model item : items) {
      if (item != null && item.getId() != null) {
        result.put(item.getId(), item);
      }
    }
    return result;
  }

  /** Ids de las entradas del JSON: maps con id (con o sin version) y entradas numéricas. */
  private Set<Long> collectIds(Collection<?> jsonItems) {
    Set<Long> ids = new HashSet<>();
    for (Object item : jsonItems) {
      if (item instanceof Map) {
        Long id = findId((Map<?, ?>) item);
        if (id != null && id > 0) {
          ids.add(id);
        }
      } else if (item instanceof Number) {
        ids.add(((Number) item).longValue());
      }
    }
    return ids;
  }

  private Long findId(Map<?, ?> values) {
    try {
      return Long.parseLong(values.get("id").toString());
    } catch (Exception e) {
      return null;
    }
  }
}
