package dev.normalbias.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.*;
import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class StorageConfiguration {
    @Bean(destroyMethod = "close")
    StorageDirectory storageDirectory(@Value("${normalbias.data-dir}") String path) throws IOException {
        return new StorageDirectory(Path.of(path), true);
    }

    @Bean
    FlywayMigrationStrategy migrationStrategy(StorageDirectory directory) {
        // Acquiring ownership must precede migrations as well as file recovery.
        return SchemaMigrations::migrate;
    }
}
