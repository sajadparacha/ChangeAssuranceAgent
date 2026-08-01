package com.company.changeassurance.application.port.out;

import java.io.InputStream;

/**
 * Stores uploaded files outside the database. Implementations must prevent path traversal.
 */
public interface FileStoragePort {

    StoredFile store(
            String originalFilename,
            String contentType,
            long sizeBytes,
            InputStream content
    );

    record StoredFile(
            String storageKey,
            String originalFilename,
            String serverFilename,
            String contentHash,
            String contentType,
            long sizeBytes,
            String absolutePath
    ) {
    }
}
