/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.modelservice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AllowProperties {

    private final Map<String, Object> allowProperties;


    public static AllowProperties createAllowProperties(Map<String, Object> allowProperties) {
        return new AllowProperties(allowProperties);
    }

    public static AllowProperties createAllowAllProperties() {
        Map<String, Object> allowAllProperties = new HashMap<>();
        allowAllProperties.put("*", null);
        return new AllowProperties(allowAllProperties);
    }

    public AllowProperties(Map<String, Object> allowProperties) {
        this.allowProperties = allowProperties;
    }


    public boolean allowProperty(String propertyName) {
        if (allowProperties == null) {
            return false;
        }

        if ("class".equals(propertyName)) {
            return false;
        }

        if (allowProperties.containsKey(propertyName) == true) {
            return true;
        }

        if (allowProperties.containsKey("*") == true) {
            if (allowProperties.get("*") != null) {
                throw new RuntimeException("Si se permite todas las propiedades con '*', no se pueden especificar propiedades concretas dentro de '*'");
            }
            if (allowProperties.size() != 1) {
                throw new RuntimeException("Si se permite todas las propiedades con '*', solo puede haber en el Map un '*'");
            }

            return true;
        }

        return false;
    }


    public AllowProperties innerAllowProperties(String propertyName) {
        if (allowProperties == null) {
            return null;
        }

        if (allowProperties.containsKey("*") == true) {
            if (allowProperties.get("*") != null) {
                throw new RuntimeException("Si se permite todas las propiedades con '*', no se pueden especificar propiedades concretas dentro de '*'");
            }
            if (allowProperties.size() != 1) {
                throw new RuntimeException("Si se permite todas las propiedades con '*', solo puede haber en el Map un '*'");
            }

            return createAllowAllProperties();
        } else {
            return createAllowProperties((Map<String, Object>) allowProperties.get(propertyName));
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> filter(Map<String, Object> source, AllowProperties allow) {
        if (source == null || allow == null) {
            return source;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();

            if ("id".equals(key) || "version".equals(key) || (key != null && key.startsWith("_"))) {
                result.put(key, value);
                continue;
            }

            if (!allow.allowProperty(key)) {
                continue;
            }

            if (value instanceof Map) {
                AllowProperties inner = allow.innerAllowProperties(key);
                result.put(key, filter((Map<String, Object>) value, inner));
            } else if (value instanceof Collection<?> col) {
                AllowProperties inner = allow.innerAllowProperties(key);
                List<Object> list = new ArrayList<>();
                for (Object item : col) {
                    if (item instanceof Map) {
                        list.add(filter((Map<String, Object>) item, inner));
                    } else {
                        list.add(item);
                    }
                }
                result.put(key, list);
            } else {
                result.put(key, value);
            }
        }
        return result;
    }
}
