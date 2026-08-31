package com.axelor.auth;

import com.axelor.auth.db.Permission;
import com.axelor.auth.db.User;
import com.axelor.db.JpaSecurity.AccessType;

import java.util.Optional;
import java.util.Set;

public interface EducaFlowAuthResolver {

    /*Optional<Boolean> hasAccess(AccessType accessType, User user, Class<? extends Model> model);

    Optional<Boolean> hasAccess(AccessType accessType, User user, Model instance);*/

    /**
     * Resuelve los permisos que el usuario tiene sobre {@code object} para el tipo de acceso dado,
     * con la misma semántica que la resolución por defecto de Axelor: permisos directos del usuario,
     * de sus roles, de su grupo y de los roles de su grupo, filtrados por tipo de acceso.
     *
     * <p>Es {@code default} a propósito: {@link AuthResolver} es package-private, así que las
     * implementaciones externas no pueden hacer {@code new AuthResolver()} ni llamar a sus métodos.
     * Este método es el puente que expone la operación (no el objeto) sin cambiar la visibilidad
     * de {@code AuthResolver}, es decir, sin divergir del upstream de Axelor. No mover el cuerpo
     * a las implementaciones: fuera de este paquete no compila.
     */
    default Set<Permission> resolveForUser(User user, String object, AccessType type) {
        return new AuthResolver().resolve(user, object, type);
    }

    public Optional<Set<Permission>> resolve(final User user, final String object, final AccessType type, Long... ids);


}
