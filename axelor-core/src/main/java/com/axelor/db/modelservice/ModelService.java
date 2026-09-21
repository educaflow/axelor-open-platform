/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.modelservice;

import com.axelor.db.Model;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for entity persistence operations.
 *
 * <p>Implement this interface to intercept save and remove operations for a specific entity type.
 * The implementation is discovered by convention: for an entity {@code com.pkg.db.MyEntity}, the
 * framework looks for {@code com.pkg.service.MyEntityService} and then {@code
 * com.pkg.service.MyEntityServiceImpl}.
 *
 * @param <T> the type of the entity
 */
public interface ModelService<T extends Model> {

  /**
   * Inserta la entidad dada por primera vez en la base de datos.
   *
   * @param entity la entidad a insertar
   * @return la entidad insertada
   */
  T insert(T entity);

  /**
   * Actualiza la entidad dada en la base de datos.
   *
   * @param entity la entidad a actualizar
   * @return la entidad actualizada
   */
  T update(T entity, T original);

  /**
   * Remove the given entity.
   *
   * @param entity the entity to remove
   */
  void remove(T entity);

  /**
   * Validate the given json map before persisting.
   *
   * @param json the json map to validate
   * @param context the context
   * @return validated json map
   */
  Map<String, Object> validate(Map<String, Object> json, Map<String, Object> context);

  public Optional<BusinessMessages> validateInsert(T entity);
  public Optional<BusinessMessages> validateUpdate(T entity,T original);
  public Optional<BusinessMessages> validateRemove(T entity);

  public AllowProperties allowPropertiesInsert();
  public AllowProperties allowPropertiesUpdate();
  public AllowProperties allowPropertiesRemove();

  /**
   * Devuelve la entidad con el id dado.
   *
   * <p>Que no exista no es un caso normal: los ids salen de referencias que ya estaban en la base de
   * datos o de la propia petición, así que no encontrarla significa que el dato está roto o
   * manipulado, no algo que el usuario de la pantalla pueda corregir.
   *
   * @param id el id de la entidad
   * @return la entidad, nunca {@code null}
   * @throws IllegalArgumentException si {@code id} es {@code null}
   * @throws EntityNotFoundException si no existe ninguna entidad con ese id
   */
  T getById(Long id);

  /**
   * Devuelve la entidad con el código dado. Solo vale para las entidades que declaran un campo
   * {@code code}.
   *
   * <p>Que no exista no es un caso normal: el código lo pone quien programa la vista o la acción, no
   * el usuario, así que no encontrarla es un error de programación.
   *
   * @param code el código de la entidad
   * @return la entidad, nunca {@code null}
   * @throws IllegalArgumentException si {@code code} es {@code null} o está en blanco
   * @throws IllegalStateException si la entidad no declara el campo {@code code}
   * @throws EntityNotFoundException si no existe ninguna entidad con ese código
   */
  T getByCode(String code);

}
