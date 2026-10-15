package com.forward.hrms.auth.repository;

import com.forward.hrms.auth.entity.Role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    List<Role> findByCodeIn(Collection<String> codes);

    List<Role> findByServiceAccountFalse();

    /** Reverse lookup for the roles page — which roles include a given permission. */
    @Query("""
            select distinct r from Role r
            join r.permissions p
            where p.code = :code
            order by r.code
            """)
    List<Role> findByPermissionCode(String code);

    boolean existsByCode(String code);

    /** True when at least one user holds the role — used to refuse DELETE. */
    @Query("""
            select case when count(u) > 0 then true else false end
            from User u join u.roles r
            where r.id = :roleId
            """)
    boolean isAssignedToAnyUser(long roleId);
}
