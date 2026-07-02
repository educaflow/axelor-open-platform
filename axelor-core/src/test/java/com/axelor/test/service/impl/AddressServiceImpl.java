/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.test.service.impl;

import com.axelor.db.Repository;
import com.axelor.db.modelservice.BusinessMessage;
import com.axelor.db.modelservice.BusinessMessages;
import com.axelor.db.modelservice.DefaultModelService;
import com.axelor.test.db.Address;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio de test para {@link Address} usado por {@code ResourceDetailValidationTest}: valida
 * por valores centinela en {@code street} (para no interferir con el resto de la suite) y cuenta
 * las invocaciones para poder afirmar qué validaciones ejecutó el {@code
 * ModelServiceValidationWalker}.
 */
public class AddressServiceImpl extends DefaultModelService<Address> {

  public static final String INVALID_INSERT = "INVALID-INSERT";
  public static final String INVALID_UPDATE = "INVALID-UPDATE";
  public static final String NO_DELETE = "NO-DELETE";

  public static final AtomicInteger INSERT_CALLS = new AtomicInteger();
  public static final AtomicInteger UPDATE_CALLS = new AtomicInteger();
  public static final AtomicInteger REMOVE_CALLS = new AtomicInteger();
  public static final AtomicReference<Address> LAST_ORIGINAL = new AtomicReference<>();

  public AddressServiceImpl(Class<Address> model, Repository<Address> repository) {
    super(model, repository);
  }

  public static void reset() {
    INSERT_CALLS.set(0);
    UPDATE_CALLS.set(0);
    REMOVE_CALLS.set(0);
    LAST_ORIGINAL.set(null);
  }

  @Override
  public Optional<BusinessMessages> validateInsert(Address address) {
    INSERT_CALLS.incrementAndGet();
    if (address.getStreet() != null && address.getStreet().startsWith(INVALID_INSERT)) {
      BusinessMessages messages = new BusinessMessages();
      messages.add(new BusinessMessage("street", "street inválido en insert"));
      return Optional.of(messages);
    }
    return Optional.empty();
  }

  @Override
  public Optional<BusinessMessages> validateUpdate(Address address, Address original) {
    UPDATE_CALLS.incrementAndGet();
    LAST_ORIGINAL.set(original);
    if (address.getStreet() != null && address.getStreet().startsWith(INVALID_UPDATE)) {
      BusinessMessages messages = new BusinessMessages();
      messages.add(new BusinessMessage("street", "street inválido en update"));
      return Optional.of(messages);
    }
    return Optional.empty();
  }

  @Override
  public Optional<BusinessMessages> validateRemove(Address address) {
    REMOVE_CALLS.incrementAndGet();
    if (address.getStreet() != null && address.getStreet().startsWith(NO_DELETE)) {
      BusinessMessages messages = new BusinessMessages();
      messages.add(new BusinessMessage("street", "no se puede borrar esta dirección"));
      return Optional.of(messages);
    }
    return Optional.empty();
  }
}
