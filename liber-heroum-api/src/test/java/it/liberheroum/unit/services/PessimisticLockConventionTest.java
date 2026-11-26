package it.liberheroum.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PessimisticLockConventionTest {

    private static final Path SERVICES = Path.of("src/main/java/it/liberheroum/services");
    private static final Path LOCKS = SERVICES.resolve("PessimisticLocks.java");

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> serviceFiles() {
        try (Stream<Path> files = Files.list(SERVICES)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void services_forUpdate_isUsedOnlyInPessimisticLocks() {
        List<String> filesWithForUpdate = serviceFiles().stream()
                .filter(file -> read(file).contains(".forUpdate()"))
                .map(file -> file.getFileName().toString())
                .toList();

        assertThat(filesWithForUpdate).containsExactly("PessimisticLocks.java");
    }

    @Test
    void pessimisticLocks_queries_filterOnlyByIdWithoutJoins() {
        String source = read(LOCKS);

        assertThat(source).contains(".idEq(").doesNotContain(".eq(", ".in(", ".fetch(", ".ne(");
    }
}
