/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.auth;

import com.axelor.auth.db.Permission;
import com.axelor.auth.db.User;
import com.axelor.common.StringUtils;
import com.axelor.db.JpaSecurity;
import com.axelor.db.Model;
import com.axelor.rpc.filter.Filter;
import com.axelor.rpc.filter.JPQLFilter;
import com.axelor.script.GroovyScriptHelper;
import com.axelor.script.ScriptBindings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.shiro.authz.UnauthorizedException;

@Singleton
class AuthSecurity implements JpaSecurity, Provider<JpaSecurity> {

  private static final class Condition {

    private Filter filter;

    public Condition(User user, String condition, String params) {

      final List<Object> args = Lists.newArrayList();

      if (StringUtils.notBlank(params)) {
        for (String param : params.split(",")) {
          param = param.trim();
          if ("__user__".equals(param)) {
            args.add(user);
          } else {
            final Object value = eval(user, "__user__", param);
            args.add(value);
          }
        }
      }

      this.filter = new JPQLFilter(condition, args.toArray());
    }

    private Object eval(Object bean, String prefix, String expr) {
      if (bean == null) {
        return null;
      }

      return new GroovyScriptHelper(new ScriptBindings(Collections.singletonMap(prefix, bean)))
          .eval(expr);
    }

    public Filter getFilter() {
      return filter;
    }

    @Override
    public String toString() {
      return filter.getQuery();
    }
  }

  private AuthResolver authResolver = new AuthResolver();
  private EduFlowAuthResolver eduFlowAuthResolver = EduFlowAuthResolverRegistry.get();

  private User getUser() {
    final User user = AuthUtils.getUser();
    if (user == null || AuthUtils.isAdmin(user)) {
      return null;
    }
    return user;
  }

  private Condition getCondition(User user, Permission permission, AccessType accessType) {
    final String condition = permission.getCondition();
    final String params = permission.getConditionParams();
    if (condition == null || "".equals(condition.trim())) {
      return null;
    }
    return new Condition(user, condition, params);
  }

  @Override
  public boolean hasRole(String name) {
    final User user = getUser();
    if (user == null) {
      return true;
    }
    return AuthUtils.hasRole(user, name);
  }

  @Override
  public Set<AccessType> getAccessTypes(Class<? extends Model> model) {
    return getAccessTypes(model, null);
  }

  @Override
  public Set<AccessType> getAccessTypes(Class<? extends Model> model, Long id) {
    final Set<AccessType> types = Sets.newHashSet();
    for (AccessType type : AccessType.values()) {
      if (id == null ? isPermitted(type, model) : isPermitted(type, model, id)) {
        types.add(type);
      }
    }
    return types;
  }

  @Override
  public Filter getFilter(AccessType type, Class<? extends Model> model, Long... ids) {
    final User user = getUser();
    if (user == null) {
      return null;
    }

    final List<Filter> filters = Lists.newArrayList();

    final Set<Permission> permissions = resolvePermissions(user, model.getName(), type, ids);
    //final Set<Permission> permissions = authResolver.resolve(user, model.getName(), type);

    if (permissions.isEmpty()) {
      return null;
    }

    for (Permission permission : permissions) {
      Condition condition = this.getCondition(user, permission, type);
      if (condition != null) {
        filters.add(condition.getFilter());
      }
    }

    if (filters.isEmpty() && ids.length == 0) {
      return null;
    }

    Filter left = filters.isEmpty() ? null : Filter.or(filters);
    Filter right = null;

    if (ids != null && ids.length > 0 && ids[0] != null) {
      right = Filter.in("id", Lists.newArrayList(ids));
    }

    if (right == null) return left;
    if (left == null) return right;

    return Filter.and(left, right);
  }

  @Override
  public boolean isPermitted(AccessType type, Class<? extends Model> model, Long... ids) {
    final User user = getUser();
    if (user == null) {
      return true;
    }

    final Set<Permission> permissions = resolvePermissions(user, model.getName(), type, ids);
    //final Set<Permission> permissions = authResolver.resolve(user, model.getName(), type);
    if (permissions.isEmpty()) {
      return false;
    }

    // check whether non-conditional permissions are granted
    for (Permission permission : permissions) {
      if (permission.getCondition() == null && authResolver.hasAccess(permission, type)) {
        return true;
      }
    }

    if (ids == null || ids.length == 0) {
      return true;
    }

    final Filter filter = this.getFilter(type, model, ids);
    if (filter == null) {
      return true;
    }

    return filter.build(model).count() == ids.length;
  }

  @Override
  public void check(AccessType type, Class<? extends Model> model, Long... ids) {
    if (isPermitted(type, model, ids)) {
      return;
    }
    final AuthSecurityException cause = new AuthSecurityException(type, model, ids);
    throw new UnauthorizedException(cause.getMessage(), cause);
  }

  @Override
  public JpaSecurity get() {
    return new AuthSecurity();
  }

  private Set<Permission> resolvePermissions (User user, String object, AccessType type, Long... ids) {
    if (eduFlowAuthResolver != null) {
      return eduFlowAuthResolver
              .resolve(user, object, type, ids).orElse(
              authResolver.resolve(user, object, type));
    }
    return authResolver.resolve(user, object, type);
  }
}
