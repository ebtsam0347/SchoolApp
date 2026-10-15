package com.forward.hrms.auth;

import com.forward.hrms.auth.service.PermissionCatalog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the permission vocabulary against drift. Plain filesystem + regex
 * (no ArchUnit / no class loading) so it runs under any JDK.
 *
 * <p>Two directions, both of which actually bit us before this guard existed:
 * <ol>
 *   <li>{@link #everyEnforcedPermissionIsKnown()} — every permission string
 *       enforced in code ({@code @RequirePermission(...)} or
 *       {@code hasPermission("...")}) is a known code in
 *       {@link PermissionCatalog}. Catches an endpoint gated on a permission
 *       nobody can ever hold — the {@code payroll.advance.*} class of bug.</li>
 *   <li>{@link #everyCatalogedPermissionIsSeeded()} — every code in
 *       {@link PermissionCatalog} is seeded by at least one auth migration.
 *       Catches a catalogued permission that no migration ever inserts.</li>
 * </ol>
 *
 * <p>The catalog is the hinge between the two: code → catalog → seed.
 */
class PermissionRegistryTest {

    private static final Pattern REQUIRE_PERMISSION =
            Pattern.compile("@RequirePermission\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern HAS_PERMISSION =
            Pattern.compile("hasPermission\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    @Test
    void everyEnforcedPermissionIsKnown() {
        Path root = servicesRoot();
        Set<String> known = PermissionCatalog.all();
        Set<String> unknown = new TreeSet<>();

        for (Path java : javaSources(root)) {
            String src = read(java);
            Matcher rp = REQUIRE_PERMISSION.matcher(src);
            while (rp.find()) {
                Matcher q = QUOTED.matcher(rp.group(1));
                while (q.find()) record(unknown, known, q.group(1), root, java);
            }
            Matcher hp = HAS_PERMISSION.matcher(src);
            while (hp.find()) record(unknown, known, hp.group(1), root, java);
        }

        assertThat(unknown)
                .as("permissions enforced in code but absent from PermissionCatalog "
                        + "(likely unseeded or a typo — add a seed migration + catalog entry)")
                .isEmpty();
    }

    @Test
    void everyCatalogedPermissionIsSeeded() {
        String migrations = readAllMigrations();
        Set<String> unseeded = new TreeSet<>();
        for (String code : PermissionCatalog.all()) {
            if (!migrations.contains("'" + code + "'")) unseeded.add(code);
        }
        assertThat(unseeded)
                .as("permissions in PermissionCatalog that no auth migration seeds")
                .isEmpty();
    }

    private static void record(Set<String> sink, Set<String> known, String code, Path root, Path file) {
        if (!known.contains(code)) sink.add(code + "  (" + root.relativize(file) + ")");
    }

    private static List<Path> javaSources(Path root) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> p.toString().endsWith(".java"))
             // Normalise separators: on Windows Path.toString() uses
             // backslashes, so the old hardcoded "/" filter matched nothing
             // and this whole scan passed vacuously.
             .filter(p -> p.toString().replace(File.separatorChar, '/')
                     .contains("/src/main/java/"))
             .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static String readAllMigrations() {
        Path dir = servicesRoot().resolve("auth/src/main/resources/db/migration");
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(p -> p.toString().endsWith(".sql")).forEach(p -> sb.append(read(p)).append('\n'));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Locate the services/ monorepo root regardless of the test working dir. */
    private static Path servicesRoot() {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        List<Path> candidates = new ArrayList<>();
        candidates.add(start);
        candidates.add(start.getParent());
        candidates.add(start.resolve("services"));
        if (start.getParent() != null) candidates.add(start.getParent().resolve("services"));
        for (Path c : candidates) {
            if (c != null
                    && Files.isDirectory(c.resolve("auth/src/main/java"))
                    && Files.isDirectory(c.resolve("attendance/src/main/java"))) {
                return c;
            }
        }
        throw new IllegalStateException("could not locate services/ root from user.dir=" + start);
    }
}
