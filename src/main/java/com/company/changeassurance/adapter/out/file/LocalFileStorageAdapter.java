package com.company.changeassurance.adapter.out.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.company.changeassurance.application.port.out.FileStoragePort;
import com.company.changeassurance.domain.exception.DomainValidationException;

@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".sql", ".pkb", ".pks", ".pls", ".ddl", ".txt");
    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/sql",
            "text/plain",
            "application/octet-stream",
            "text/x-sql"
    );

    private final Path rootDirectory;
    private final long maxBytes;

    public LocalFileStorageAdapter(
            @Value("${changeassurance.storage.root:./uploads}") String root,
            @Value("${changeassurance.storage.max-bytes:1048576}") long maxBytes
    ) throws IOException {
        this.rootDirectory = Path.of(root).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        Files.createDirectories(this.rootDirectory);
    }

    @Override
    public StoredFile store(String originalFilename, String contentType, long sizeBytes, InputStream content) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new DomainValidationException("Original filename is required");
        }
        if (content == null) {
            throw new DomainValidationException("File content is required");
        }
        if (sizeBytes <= 0) {
            throw new DomainValidationException("Empty files are rejected");
        }
        if (sizeBytes > maxBytes) {
            throw new DomainValidationException("File exceeds maximum allowed size of " + maxBytes + " bytes");
        }

        String safeOriginal = Path.of(originalFilename).getFileName().toString();
        String extension = extensionOf(safeOriginal);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new DomainValidationException("File extension not allowed: " + extension);
        }
        String mime = contentType == null ? "application/octet-stream" : contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME.contains(mime) && !mime.startsWith("text/")) {
            throw new DomainValidationException("MIME type not allowed: " + mime);
        }

        String serverFilename = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = rootDirectory.resolve(serverFilename).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new DomainValidationException("Path traversal rejected");
        }

        try {
            byte[] bytes = content.readAllBytes();
            if (bytes.length == 0) {
                throw new DomainValidationException("Empty files are rejected");
            }
            if (bytes.length > maxBytes) {
                throw new DomainValidationException("File exceeds maximum allowed size of " + maxBytes + " bytes");
            }
            if (looksBinary(bytes)) {
                throw new DomainValidationException("Binary content rejected");
            }
            Files.write(target, bytes);
            String hash = sha256(bytes);
            return new StoredFile(
                    serverFilename,
                    safeOriginal,
                    serverFilename,
                    hash,
                    mime,
                    bytes.length,
                    target.toString()
            );
        } catch (DomainValidationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new DomainValidationException("Failed to store uploaded file: " + ex.getMessage());
        }
    }

    public String readUtf8(String storageKey) {
        Path target = rootDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new DomainValidationException("Path traversal rejected");
        }
        try {
            return Files.readString(target);
        } catch (IOException ex) {
            throw new DomainValidationException("Failed to read stored file");
        }
    }

    private static String extensionOf(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return name.substring(idx).toLowerCase(Locale.ROOT);
    }

    private static boolean looksBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 512);
        int control = 0;
        for (int i = 0; i < limit; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0) {
                return true;
            }
            if (b < 7 || (b > 13 && b < 32)) {
                control++;
            }
        }
        return control > limit / 10;
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
