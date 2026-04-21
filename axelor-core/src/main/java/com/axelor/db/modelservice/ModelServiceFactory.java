/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.modelservice;

import com.axelor.db.Model;
import com.axelor.db.Repository;

/** Factoría que resuelve el {@link ModelService} para una clase de entidad dada. */
public interface ModelServiceFactory {

  <U extends Model> ModelService<U> resolve(Class<U> modelClass, Repository<U> repository);

  <U extends Model> ModelService<U> resolve(Class<U> modelClass);
}