package com.mehedi.waylo.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);
    private static final long MAX_BYTES = 6L * 1024 * 1024;
    private static final long MAX_PIXELS = 25_000_000L;

    public enum PhotoKind {
        POST("posts"), PROFILE("profiles"), TRIP("trips"), DESTINATION("destinations");
        private final String folder;
        PhotoKind(String folder) { this.folder = folder; }
    }

    public record StoredPhoto(String url, String publicId) { }

    private final Cloudinary cloudinary;
    private final Path legacyDirectory;

    @org.springframework.beans.factory.annotation.Autowired
    public MediaStorageService(
            @Value("${waylo.cloudinary-url:}") String cloudinaryUrl,
            @Value("${waylo.upload-directory:./waylo-uploads}") String legacyDirectory) {
        this.cloudinary = cloudinaryUrl == null || cloudinaryUrl.isBlank()
                ? null : new Cloudinary(cloudinaryUrl.trim());
        this.legacyDirectory = Path.of(legacyDirectory).toAbsolutePath().normalize();
    }

    MediaStorageService(Cloudinary cloudinary, Path legacyDirectory) {
        this.cloudinary = cloudinary;
        this.legacyDirectory = legacyDirectory.toAbsolutePath().normalize();
    }

    // Old /media/UUID.jpg URLs continue to work if the old files remain available.

    public StoredPhoto store(MultipartFile file, PhotoKind kind) {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Choose a photo smaller than 6 MB.");
        }
        if (cloudinary == null) {
            throw new IllegalArgumentException(
                    "Photo uploads are not configured. Set CLOUDINARY_URL in the server environment.");
        }

        // Decode and re-encode to JPEG to strip EXIF/GPS and disallow arbitrary payloads.
        byte[] imageBytes = sanitizedJpeg(file);
        String folder = "waylo/" + kind.folder;
        String expectedId = folder + "/" + UUID.randomUUID();

        try {
            Map<?, ?> result = cloudinary.uploader().upload(imageBytes, ObjectUtils.asMap(
                    "resource_type", "image",
                    "public_id", expectedId,
                    "asset_folder", folder,
                    "overwrite", false,
                    "unique_filename", false
            ));
            String secureUrl = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));

            if (!expectedId.equals(publicId) || !validHttps(secureUrl)
                    || secureUrl.length() > 500) {
                deleteQuietly(expectedId);
                throw new IllegalArgumentException("Cloudinary returned invalid photo metadata.");
            }

            // If the DB transaction rolls back, remove the uploaded cloud asset.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status != STATUS_COMMITTED) deleteQuietly(publicId);
                            }
                        });
            }
            return new StoredPhoto(secureUrl, publicId);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cloudinary photo upload failed: {}", ex.getClass().getSimpleName());
            throw new IllegalArgumentException("Photo upload failed. Please try again.");
        }
    }

    private boolean validHttps(String address) {
        try {
            URI uri = URI.create(address);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] sanitizedJpeg(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw new IllegalArgumentException("Invalid image file.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Only JPEG or PNG photographs are supported.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw new IllegalArgumentException("Only JPEG or PNG photographs are supported.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("Photo dimensions are too large.");
                }
                BufferedImage inputImage = reader.read(0);
                BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = output.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, width, height);
                    graphics.drawImage(inputImage, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!ImageIO.write(output, "jpeg", bytes)) {
                    throw new IllegalArgumentException("Could not encode the photograph.");
                }
                if (bytes.size() > MAX_BYTES) {
                    throw new IllegalArgumentException("Photo exceeds 6 MB after processing.");
                }
                return bytes.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the uploaded photograph.");
        }
    }

    // Delete only assets created under Waylo's own Cloudinary public-ID prefix.
    private void deleteQuietly(String publicId) {
        if (cloudinary == null || publicId == null || !publicId.matches(
                "waylo/(posts|profiles|trips|destinations)/[0-9a-fA-F-]{36}")) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", "image", "invalidate", true));
        } catch (Exception ex) {
            log.warn("Could not clean up a Cloudinary asset: {}", ex.getClass().getSimpleName());
        }
    }

    // Never delete a previous photo until MySQL commits the replacement/deletion.
    public void deleteAfterCommit(String publicId, String oldUrl) {
        if ((publicId == null || publicId.isBlank())
                && (oldUrl == null || !oldUrl.startsWith("/media/"))) return;
        Runnable cleanup = () -> {
            deleteQuietly(publicId);
            if (oldUrl != null && oldUrl.startsWith("/media/")) {
                try {
                    Files.deleteIfExists(getPublicImage(oldUrl.substring("/media/".length())));
                } catch (IOException | ResponseStatusException ex) {
                    log.warn("Could not clean up an old local photo.");
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
    }

    // Legacy file endpoint serves photos created before switching to Cloudinary.
    public Path getPublicImage(String filename) {
        if (filename == null || !filename.matches("[0-9a-fA-F-]{36}\\.jpg")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path resolved = legacyDirectory.resolve(filename).normalize();
        if (!resolved.startsWith(legacyDirectory) || !Files.isRegularFile(resolved)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return resolved;
    }
}
