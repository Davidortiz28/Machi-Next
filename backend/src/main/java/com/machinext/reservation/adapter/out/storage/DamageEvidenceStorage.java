package com.machinext.reservation.adapter.out.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class DamageEvidenceStorage {
    private final Path root;

    public DamageEvidenceStorage(@Value("${app.evidence.storage-directory:./uploads/evidence}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) throws IOException {
        Files.createDirectories(root);
        String storageKey = UUID.randomUUID().toString();
        try (var input = file.getInputStream()) {
            Files.copy(input, resolve(storageKey));
        }
        return storageKey;
    }

    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el archivo de evidencia");
        }
        return new FileSystemResource(path);
    }

    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || storageKey.contains("/") || storageKey.contains("\\")) {
            throw new IllegalArgumentException("Identificador de evidencia inválido");
        }
        return resolved;
    }
}
