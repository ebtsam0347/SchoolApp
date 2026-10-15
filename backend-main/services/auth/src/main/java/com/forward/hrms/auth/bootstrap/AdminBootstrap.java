package com.forward.hrms.auth.bootstrap;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.auth.entity.Role;
import com.forward.hrms.auth.entity.User;
import com.forward.hrms.auth.enums.UserStatus;
import com.forward.hrms.auth.repository.RoleRepository;
import com.forward.hrms.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    @Value("${hrms.auth.bootstrap-admin.username:admin}")
    private String username;

    @Value("${hrms.auth.bootstrap-admin.password:ChangeMe!2026}")
    private String password;

    @Value("${hrms.auth.bootstrap-admin.full-name:System Administrator}")
    private String fullName;

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (users.existsByUsername(username)) return;

        Role adminRole = roles.findByCode("ADMIN").orElseThrow(() ->
                new IllegalStateException("ADMIN role missing — V0003 seed didn't run?"));

        User admin = new User();
        admin.setUsername(username);
        admin.setFullName(fullName);
        admin.setPasswordHash(encoder.encode(password));
        admin.setStatus(UserStatus.ACTIVE);
        admin.setMustChangePw(true);
        admin.setEmployeeLinked(false);
        Set<Role> r = new HashSet<>();
        r.add(adminRole);
        admin.setRoles(r);
        users.save(admin);

        log.warn("Bootstrapped admin user '{}' with temporary password from "
                + "hrms.auth.bootstrap-admin.password. Change it on first login.", username);
    }
}
