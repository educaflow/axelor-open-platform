/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.modelservice.impl;

import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.Repository;
import com.axelor.db.modelservice.DefaultModelService;
import com.axelor.db.modelservice.ModelService;
import com.axelor.db.modelservice.ModelServiceFactory;
import com.axelor.inject.Beans;
import com.google.inject.Injector;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModelServiceFactoryImpl implements ModelServiceFactory {

  @Override
  @SuppressWarnings("unchecked")
  /**
   * Resuelve el {@link ModelService} para la clase de entidad y repositorio dados.
   *
   * <p>Para una entidad {@code com.pkg.db.MiEntidad} o {@code com.pkg.MiEntidad}, se prueban las
   * siguientes clases candidatas en orden (solo clases concretas, no interfaces):
   *
   * <ol>
   *   <li>{@code com.pkg.service.MiEntidadService}
   *   <li>{@code com.pkg.service.MiEntidadServiceImpl}
   *   <li>{@code com.pkg.service.impl.MiEntidadService}
   *   <li>{@code com.pkg.service.impl.MiEntidadServiceImpl}
   * </ol>
   *
   * <p>La clase encontrada debe:
   *
   * <ul>
   *   <li>Implementar {@link ModelService} — de lo contrario se lanza {@link IllegalStateException}.
   *   <li>Tener un constructor {@code (Class<T> model, Repository<T> repository)} — de lo contrario
   *       se lanza {@link IllegalStateException}.
   * </ul>
   *
   * <p>Tras la instanciación se llama a {@code injector.injectMembers()} para que Guice inyecte
   * los campos y métodos anotados con {@code @Inject} en el servicio.
   *
   * <p>Si no se encuentra ninguna clase candidata, se devuelve un {@link DefaultModelService}
   * (también pasado por {@code injectMembers}).
   *
   * @param <U> el tipo de la entidad
   * @param modelClass la clase de la entidad
   * @param repository el repositorio de la entidad
   * @return el servicio resuelto, nunca null
   */
  public <U extends Model> ModelService<U> resolve(Class<U> modelClass, Repository<U> repository) {

    final Injector injector = Beans.get(Injector.class);
    final String simpleName = modelClass.getSimpleName();
    final String basePackage = computeBasePackage(modelClass.getPackageName());

    final String[] candidates = {
      basePackage + ".service." + simpleName + "Service",
      basePackage + ".service." + simpleName + "ServiceImpl",
      basePackage + ".service.impl." + simpleName + "Service",
      basePackage + ".service.impl." + simpleName + "ServiceImpl",
    };

    final List<Class<?>> found = new ArrayList<>();
    for (String className : candidates) {
      Class<?> serviceClass;
      try {
        serviceClass = Class.forName(className);
      } catch (ClassNotFoundException e) {
        continue;
      }

      if (serviceClass.isInterface()) {
        continue;
      }

      found.add(serviceClass);
    }

    if (found.size() > 1) {
      String classes = found.stream().map(Class::getName).collect(Collectors.joining(", "));
      throw new IllegalStateException(
          "Se ha encontrado más de un ModelService para "
              + modelClass.getName()
              + ": "
              + classes);
    }

    if (found.size() == 1) {
      Class<?> serviceClass = found.get(0);

      if (!ModelService.class.isAssignableFrom(serviceClass)) {
        throw new IllegalStateException(
            "La clase "
                + serviceClass.getName()
                + " no implementa ModelService<"
                + simpleName
                + ">");
      }

      ModelService<U> service;
      try {
        Constructor<?> constructor = serviceClass.getConstructor(Class.class, Repository.class);
        service = (ModelService<U>) constructor.newInstance(modelClass, repository);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "La clase "
                + serviceClass.getName()
                + " debe declarar un constructor "
                + serviceClass.getSimpleName()
                + "(Class<"
                + simpleName
                + ">, Repository<"
                + simpleName
                + ">)");
      } catch (Exception e) {
        throw new IllegalStateException("Error al instanciar " + serviceClass.getName(), e);
      }

      injector.injectMembers(service);
      return service;
    }

    DefaultModelService<U> defaultService = new DefaultModelService<>(modelClass, repository);
    injector.injectMembers(defaultService);
    return defaultService;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <U extends Model> ModelService<U> resolve(Class<U> modelClass) {
    final Repository repository = JpaRepository.of(modelClass);
    return resolve(modelClass, repository);
  }

  private static String computeBasePackage(String packageName) {
    if (packageName.endsWith(".db")) {
      return packageName.substring(0, packageName.length() - ".db".length());
    }
    return packageName;
  }
}