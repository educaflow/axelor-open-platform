/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.modelservice;

import com.axelor.db.Model;
import com.axelor.db.Repository;

import jakarta.validation.ValidationException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ModelService} that delegates to the entity's {@link
 * Repository}.
 *
 * <p>This implementation is used when no custom service is found for an entity type.
 *
 * @param <T> the type of the entity
 */
public class DefaultModelService<T extends Model> implements ModelService<T> {

  protected Class<T> model;
  protected final Repository<T> repository;

  public DefaultModelService(Class<T> model,Repository<T> repository) {
    this.model=model;
    this.repository = repository;
  }

  @Override
  public T insert(T entity) {

    this.validateInsert(entity).ifPresent(this::throwIfInvalid);

    return repository.save(entity);
  }

  @Override
  public T update(T entity,T original) {

    this.validateUpdate(entity, original).ifPresent(this::throwIfInvalid);

    return repository.save(entity);
  }

  @Override
  public void remove(T entity) {

    this.validateRemove(entity).ifPresent(this::throwIfInvalid);

    repository.remove(entity);
  }

  protected void throwIfInvalid(BusinessMessages messages) {
    if (messages.isValid()) {
      return;
    }
    String text = messages.stream()
        .map(BusinessMessage::getMessage)
        .collect(Collectors.joining("\n"));
    throw new ValidationException(text);
  }

  @Override
  public Map<String, Object> validate(Map<String, Object> json, Map<String, Object> context) {
    return repository.validate(json, context);
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

}
