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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class ModelServiceFactoryImpl implements ModelServiceFactory {

  private final Logger logger = LoggerFactory.getLogger(ModelServiceFactoryImpl.class);

  /**
   * Caché de la resolución de clase de servicio por entidad. Se cachea el constructor (o
   * {@code Optional.empty()} cuando la entidad no tiene servicio propio y se usa {@link
   * DefaultModelService}); la instancia NO se cachea porque recibe el {@code repository} por
   * parámetro y pasa por {@code injectMembers} en cada resolución.
   */
  private static final ConcurrentMap<Class<?>, Optional<Constructor<?>>> CONSTRUCTOR_CACHE =
      new ConcurrentHashMap<>();

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

    // computeIfAbsent es atómico; si findServiceConstructor lanza IllegalStateException
    // (misconfiguración), la caché no se puebla y el error se repite en cada llamada.
    final Optional<Constructor<?>> cachedConstructor = CONSTRUCTOR_CACHE.computeIfAbsent(modelClass, this::findServiceConstructor);

    if (cachedConstructor.isEmpty()) {
      DefaultModelService<U> defaultService = new DefaultModelService<>(modelClass, repository);
      injector.injectMembers(defaultService);
      return defaultService;
    }

    final Constructor<?> constructor = cachedConstructor.get();
    ModelService<U> service;
    try {
      service = (ModelService<U>) constructor.newInstance(modelClass, repository);
    } catch (Exception e) {
      throw new IllegalStateException("Error al instanciar " + constructor.getDeclaringClass().getName(), e);
    }

    injector.injectMembers(service);
    return service;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <U extends Model> ModelService<U> resolve(Class<U> modelClass) {
    final Repository repository = JpaRepository.of(modelClass);
    return resolve(modelClass, repository);
  }

  /**
   * Busca la clase de servicio de la entidad entre los 4 FQN candidatos y devuelve su constructor
   * {@code (Class, Repository)}, u {@code Optional.empty()} si la entidad no tiene servicio propio
   * (ruta {@link DefaultModelService}). Se ejecuta una sola vez por entidad (ver caché).
   */
  private Optional<Constructor<?>> findServiceConstructor(Class<?> modelClass) {

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
        logger.debug("resolve: probando clase candidata {}", className);
        serviceClass = Class.forName(className);
        logger.debug("\t\t\tEncontrada {}", className);
      } catch (ClassNotFoundException e) {
        logger.debug("\t\t\tNOO encontrada {}", className);
        continue;
      }

      if (serviceClass.isInterface()) {
        logger.debug("\t\t\tDescartada al ser un interface {}", className);
        continue;
      }

      found.add(serviceClass);
    }

    if (found.size() > 1) {
      String classes = found.stream().map(Class::getName).collect(Collectors.joining(", "));
      throw new IllegalStateException("Se ha encontrado más de un ModelService para " + modelClass.getName() + ": " + classes);
    }

    if (found.isEmpty()) {
      logger.debug("Se usa el servicio por defecto  {}", DefaultModelService.class.getName());
      return Optional.empty();
    }

    Class<?> serviceClass = found.get(0);
    logger.debug("Se usa la clase  {}", serviceClass.getName());
    if (!ModelService.class.isAssignableFrom(serviceClass)) {
      throw new IllegalStateException("La clase " + serviceClass.getName() + " no implementa ModelService<" + simpleName + ">");
    }

    try {
      return Optional.of(serviceClass.getConstructor(Class.class, Repository.class));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("La clase " + serviceClass.getName() + " debe declarar un constructor " + serviceClass.getSimpleName() + "(Class<" + simpleName + ">, Repository<" + simpleName + ">)");
    }
  }

  private static String computeBasePackage(String packageName) {
    if (packageName.endsWith(".db")) {
      return packageName.substring(0, packageName.length() - ".db".length());
    }
    return packageName;
  }
}
