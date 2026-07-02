/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.test.service.impl;

import com.axelor.db.Repository;
import com.axelor.db.modelservice.BusinessMessage;
import com.axelor.db.modelservice.BusinessMessages;
import com.axelor.db.modelservice.DefaultModelService;
import com.axelor.test.db.AddressNote;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de test para la entidad nieta {@link AddressNote}: permite comprobar que el {@code
 * ModelServiceValidationWalker} desciende recursivamente (Contact → Address → AddressNote).
 */
public class AddressNoteServiceImpl extends DefaultModelService<AddressNote> {

  public static final String INVALID_NOTE = "INVALID-NOTE";

  public static final AtomicInteger INSERT_CALLS = new AtomicInteger();

  public AddressNoteServiceImpl(Class<AddressNote> model, Repository<AddressNote> repository) {
    super(model, repository);
  }

  public static void reset() {
    INSERT_CALLS.set(0);
  }

  @Override
  public Optional<BusinessMessages> validateInsert(AddressNote addressNote) {
    INSERT_CALLS.incrementAndGet();
    if (addressNote.getNote() != null && addressNote.getNote().startsWith(INVALID_NOTE)) {
      BusinessMessages messages = new BusinessMessages();
      messages.add(new BusinessMessage("note", "nota inválida"));
      return Optional.of(messages);
    }
    return Optional.empty();
  }
}
