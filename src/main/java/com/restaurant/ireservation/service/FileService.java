package com.restaurant.ireservation.service;


import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    @Value("${cascas.upload-file.base-uri}")
    private String absolutelyPathServer;
    public static final String avatar = "user-avatars";

    public String getNameAvatarUrl(MultipartFile file) throws URISyntaxException, IOException {
        var uploadPath = absolutelyPathServer + avatar;
        var directoryPath = Paths.get(uploadPath);
        Files.createDirectories(directoryPath);
        var originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IOException("File name invalid");
        }
        var fileName = "cascas-" + UUID.randomUUID() + ".webp";
        var filePath = directoryPath.resolve(fileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        return avatar + "/" + fileName;


    }
}
