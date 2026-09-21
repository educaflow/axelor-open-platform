/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.modelservice;

import com.axelor.db.Model;
import com.axelor.db.Repository;
import com.axelor.db.mapper.Mapper;
import jakarta.persistence.EntityNotFoundException;

import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link ModelService} that delegates to the entity's {@link
 * Repository}.
 *
 * <p>This implementation is used when no custom service is found for an entity type.
 *
 * @param <T> the type of the entity
 */
public class DefaultModelService<T extends Model> implements ModelService<T> {

  /** El nombre del campo por el que busca {@link #getByCode(String)}. */
  private static final String CAMPO_CODE = "code";

  protected Class<T> model;
  protected final Repository<T> repository;

  public DefaultModelService(Class<T> model,Repository<T> repository) {
    this.model=model;
    this.repository = repository;
  }

  @Override
  public T insert(T entity) {
    validateInsert(entity).ifPresent(BusinessMessages::throwIfInvalid);
    return repository.save(entity);
  }

  @Override
  public T update(T entity,T original) {
    validateUpdate(entity, original).ifPresent(BusinessMessages::throwIfInvalid);
    return repository.save(entity);
  }

  @Override
  public void remove(T entity) {
    validateRemove(entity).ifPresent(BusinessMessages::throwIfInvalid);
    repository.remove(entity);
  }

  @Override
  public Map<String, Object> validate(Map<String, Object> json, Map<String, Object> context) {
    final Long id = findId((Map) json);
    final boolean isNew = (id == null || id <= 0L);

    AllowProperties allowProperties;

    if (isNew) {
      allowProperties=allowPropertiesInsert();
    } else {
      allowProperties=allowPropertiesUpdate();
    }

    Map<String, Object> filtered = AllowProperties.filter(json, allowProperties);

    return repository.validate(filtered, context);
  }




  @Override
  public Optional<BusinessMessages> validateInsert(T entity) {
    return Optional.empty();
  }

  @Override
  public Optional<BusinessMessages> validateUpdate(T entity,T original) {
    return Optional.empty();
  }

  @Override
  public Optional<BusinessMessages> validateRemove(T entity) {
    return Optional.empty();
  }

  @Override
  public AllowProperties allowPropertiesInsert() {
    return AllowProperties.createAllowAllProperties();
  }

  @Override
  public AllowProperties allowPropertiesUpdate() {
    return AllowProperties.createAllowAllProperties();
  }

  @Override
  public AllowProperties allowPropertiesRemove() {
    return AllowProperties.createAllowAllProperties();
  }

  @Override
  public T getById(Long id) {
    if (id == null) {
      throw new IllegalArgumentException("Se necesita un id para buscar un " + model.getName() + ", y ha llegado null.");
    }

    return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("No existe ningún " + model.getName() + " con el id: " + id));
  }

  @Override
  public T getByCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Se necesita un código para buscar un " + model.getName() + ", y ha llegado vacío.");
    }

    if (Mapper.of(model).getProperty(CAMPO_CODE) == null) {
      throw new IllegalStateException("La entidad " + model.getName() + " no tiene campo '" + CAMPO_CODE + "', así que no se puede buscar por código. getByCode solo vale para las entidades que lo declaran.");
    }

    T entity = repository.all().filter("self." + CAMPO_CODE + " = :" + CAMPO_CODE).bind(CAMPO_CODE, code).fetchOne();

    if (entity == null) {
      throw new EntityNotFoundException("No existe ningún " + model.getName() + " con el código: " + code);
    }

    return entity;
  }

  /**
   * Esta función es copia de la que hay en Resource
   * Se ha copiado porque debe hacer exactamente lo mismo
   * @param values
   * @return
   */
  private Long findId(Map<String, Object> values) {
    try {
      return Long.parseLong(values.get("id").toString());
    } catch (Exception e) {
    }
    return null;
  }


}
