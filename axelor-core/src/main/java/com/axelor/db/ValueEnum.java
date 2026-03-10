/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db;

import com.axelor.db.annotations.EnumWidget;

import java.util.Objects;

/** Enum type fields with custom values should implement this interface. */
public interface ValueEnum<T> {

  /**
   * Get the value.
   *
   * @return custom value associated with this enum constant.
   */
  T getValue();

  /**
   * Get the constant of the specified enum type with the specified value.
   *
   * @param <T> the enum type whose constant is to be returned
   * @param enumType the {@code Class} object of the enum type from which to return a constant
   * @param value the value of the constant to return
   * @return the enum constant of the specified enum type with the specified value
   * @throws NullPointerException if the specified value is null
   * @throws IllegalArgumentException if specified enumType is not an enum or no constant found for
   *     the specified value
   */
  static <T extends Enum<T>> T of(Class<T> enumType, Object value) {
    if (value == null) {
      throw new NullPointerException("Value is null.");
    }
    if (value instanceof Enum) {
      return enumType.cast(value);
    }
    if (ValueEnum.class.isAssignableFrom(enumType)) {
      for (T item : enumType.getEnumConstants()) {
        if (Objects.equals(((ValueEnum<?>) item).getValue(), value)) {
          return item;
        }
      }
    }
    if (value instanceof String string) {
      try {
        return Enum.valueOf(enumType, string);
      } catch (Exception e) {
      }
    }
    throw new IllegalArgumentException(
        "No enum constant found in " + enumType.getCanonicalName() + " for value: " + value);
  }

  /**
   * Obtiene el título definido en la anotación @EnumWidget.
   *
   * @return el título si existe la anotación, o el nombre de la constante si no.
   */
  default String getTitle() {
    if (this instanceof Enum) {
      Enum<?> enumInstance = (Enum<?>) this;
      try {
        EnumWidget enumWidget = enumInstance.getClass().getField(enumInstance.name()).getAnnotation(EnumWidget.class);

        if (enumWidget != null && enumWidget.title() != null && !enumWidget.title().isEmpty()) {
          return enumWidget.title();
        } else {
          return enumInstance.name();
        }

      } catch (NoSuchFieldException | SecurityException e) {
        return enumInstance.name();
      }
    }
    return String.valueOf(getValue());
  }

  /**
   * Obtiene la descripción definido en la anotación @EnumWidget.
   *
   * @return La descripción si existe la anotación, o el titulo si no existe o sino nombre de la constante si no.
   */
  default String getDescription() {
    if (this instanceof Enum) {
      Enum<?> enumInstance = (Enum<?>) this;
      try {
        EnumWidget enumWidget = enumInstance.getClass().getField(enumInstance.name()).getAnnotation(EnumWidget.class);

        if (enumWidget != null && enumWidget.description() != null && !enumWidget.description().isEmpty()) {
          return enumWidget.description();
        } else {
          return this.getTitle();
        }

      } catch (NoSuchFieldException | SecurityException e) {
        return this.getTitle();
      }
    }
    return String.valueOf(getValue());
  }

}
