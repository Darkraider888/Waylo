package com.mehedi.waylo.controller;

import com.mehedi.waylo.service.MediaStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class MediaController {
    private final MediaStorageService media;

    public MediaController(MediaStorageService media) {
        this.media = media;
    }

    @GetMapping("/media/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(new FileSystemResource(media.getPublicImage(filename)));
    }
}
