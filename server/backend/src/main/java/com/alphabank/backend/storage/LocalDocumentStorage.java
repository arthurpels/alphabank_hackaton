package com.alphabank.backend.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalDocumentStorage {

    private final Path root;

    public LocalDocumentStorage(@Value("${app.documents-dir}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось создать каталог хранения документов " + this.root, e);
        }
    }

    public Path store(UUID id, String extension, byte[] content) {
        Path target = root.resolve(id + "." + extension);
        try {
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить файл " + target.getFileName(), e);
        }
        return root.relativize(target);
    }

    public byte[] load(Path relative) {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Недопустимый путь файла");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать файл " + target.getFileName(), e);
        }
    }
}
