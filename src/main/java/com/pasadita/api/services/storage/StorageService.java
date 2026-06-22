package com.pasadita.api.services.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Uploads a file to the configured object storage bucket under the given folder
     * and returns the full public URL of the stored object.
     *
     * @param file   the file to upload
     * @param folder logical folder/prefix to store the object under (e.g. "products")
     * @return the public URL of the uploaded object
     */
    String uploadFile(MultipartFile file, String folder);
}
