package com.axelor.auth;

import com.axelor.auth.db.Permission;
import com.axelor.auth.db.User;
import com.axelor.db.JpaSecurity.AccessType;
import com.axelor.db.Model;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;

import java.util.Optional;
import java.util.Set;

public interface EduFlowAuthResolver {

    /*Optional<Boolean> hasAccess(AccessType accessType, User user, Class<? extends Model> model);

    Optional<Boolean> hasAccess(AccessType accessType, User user, Model instance);*/
    default boolean hasAccess(Permission permission, AccessType accessType) {
        if (accessType == null) {
            return true;
        }
        switch (accessType) {
            case READ:
                return Boolean.TRUE.equals(permission.getCanRead());
            case WRITE:
                return Boolean.TRUE.equals(permission.getCanWrite());
            case CREATE:
                return Boolean.TRUE.equals(permission.getCanCreate());
            case REMOVE:
                return Boolean.TRUE.equals(permission.getCanRemove());
            case IMPORT:
                return Boolean.TRUE.equals(permission.getCanImport());
            case EXPORT:
                return Boolean.TRUE.equals(permission.getCanExport());
            default:
                return false;
        }
    }

    private Set<Permission> filterPermissions(
            final Set<Permission> permissions, final String object, final AccessType type) {
        final Set<Permission> all = Sets.newLinkedHashSet();
        if (permissions == null || permissions.isEmpty()) {
            return all;
        }

        // add object permissions
        for (final Permission permission : permissions) {
            if (Objects.equal(object, permission.getObject()) && hasAccess(permission, type)) {
                all.add(permission);
            }
        }

        // add wild card permissions
        final String pkg = object.substring(0, object.lastIndexOf('.')) + ".*";
        for (final Permission permission : permissions) {
            if (Objects.equal(pkg, permission.getObject()) && hasAccess(permission, type)) {
                all.add(permission);
            }
        }

        return all;
    }

    public Optional<Set<Permission>> resolve(final User user, final String object, final AccessType type, Long... ids);


}
