package org.kore.myapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Imports name the classes a file uses (constitution Principle I): a wildcard import hides what a file depends on
 * and can silently change meaning when a package gains a class.
 */
class ImportStyleTest {

    private static final Pattern WILDCARD_IMPORT = Pattern.compile("^import\\s+(static\\s+)?[\\w.]+\\.\\*;", Pattern.MULTILINE);

    @Test
    void noSourceFileUsesAWildcardImport() throws IOException {
        List<String> offenders = Stream.of(Path.of("src/main/java"), Path.of("src/test/java"))
                .flatMap(ImportStyleTest::javaFiles)
                .filter(ImportStyleTest::hasWildcardImport)
                .map(Path::toString)
                .sorted()
                .toList();

        assertThat(offenders).as("files with wildcard imports").isEmpty();
    }

    @Test
    void theDetectorRecognizesWildcardImports() {
        assertThat(WILDCARD_IMPORT.matcher("import lombok.*;").find()).isTrue();
        assertThat(WILDCARD_IMPORT.matcher("import static org.assertj.core.api.Assertions.*;").find()).isTrue();
        assertThat(WILDCARD_IMPORT.matcher("import lombok.Getter;").find()).isFalse();
    }

    private static Stream<Path> javaFiles(Path root) {
        try {
            return Files.walk(root).filter(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan " + root, e);
        }
    }

    private static boolean hasWildcardImport(Path file) {
        try {
            return WILDCARD_IMPORT.matcher(Files.readString(file)).find();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
