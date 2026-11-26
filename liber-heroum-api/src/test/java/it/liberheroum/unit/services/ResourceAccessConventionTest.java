package it.liberheroum.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ResourceAccessConventionTest {

    private static final Path SERVICES = Path.of("src/main/java/it/liberheroum/services");
    private static final String GUARD = "ResourceAccessGuard.java";

    private record Statement(Path file, String code) {

        boolean isOwnedLookupById() {
            return code.contains("database.find(") && code.contains(".idEq(") && code.contains("subject\"");
        }

        String location() {
            return file.getFileName() + ": " + code.strip().lines().findFirst().orElse("");
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Statement> statements(Path file) {
        return Arrays.stream(read(file).split(";")).map(code -> new Statement(file, code)).toList();
    }

    private static List<Path> serviceFiles() {
        try (Stream<Path> files = Files.list(SERVICES)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void services_ownedLookupById_isOnlyInResourceAccessGuard() {
        List<String> outside = serviceFiles().stream()
                .filter(file -> !file.getFileName().toString().equals(GUARD))
                .flatMap(file -> statements(file).stream())
                .filter(Statement::isOwnedLookupById)
                .map(Statement::location)
                .toList();

        assertThat(outside).isEmpty();
    }
}
