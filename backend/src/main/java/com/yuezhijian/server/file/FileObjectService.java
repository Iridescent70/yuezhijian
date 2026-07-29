package com.yuezhijian.server.file;

import com.yuezhijian.server.common.ResourceNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileObjectService {
    private static final Map<String, String> UPLOAD_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf");
    private static final Map<String, String> GENERATED_EXTENSIONS = Map.of("text/csv", ".csv");

    private final FileObjectRepository repository;
    private final ObjectStorage storage;
    private final FileStorageProperties properties;

    public FileObjectService(
            FileObjectRepository repository, ObjectStorage storage, FileStorageProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    public List<BusinessAttachmentItem> attachments(String businessType, long businessId) {
        return repository.attachments(businessType, businessId);
    }

    public BusinessAttachmentItem upload(
            String businessType,
            long businessId,
            long storeId,
            String purpose,
            String category,
            MultipartFile upload,
            long operatorId) {
        if (repository.countActive(businessType, businessId) >= properties.maxAttachmentsPerBusiness()) {
            throw new IllegalArgumentException("单个业务单最多上传" + properties.maxAttachmentsPerBusiness() + "个附件");
        }
        byte[] content = read(upload);
        String name = normalizeName(upload.getOriginalFilename());
        String contentType = detectContentType(content);
        validateExtension(name, contentType);
        String objectKey = objectKey(purpose, contentType);
        FileObjectDraft file = new FileObjectDraft(
                objectKey, name, contentType, content.length, sha256(content), purpose, operatorId);
        storage.put(objectKey, content, contentType);
        try {
            return repository.createAndAttach(
                    file, new AttachmentDraft(businessType, businessId, storeId, category, operatorId));
        } catch (RuntimeException exception) {
            try {
                storage.delete(objectKey);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    public FileObjectItem storeGenerated(
            String purpose, String originalName, String contentType, byte[] content, long operatorId) {
        if (!GENERATED_EXTENSIONS.containsKey(contentType)) {
            throw new IllegalArgumentException("暂不支持该任务结果文件类型");
        }
        if (content == null || content.length == 0) throw new IllegalArgumentException("任务结果文件不能为空");
        if (content.length > properties.maxUploadBytes()) {
            throw new IllegalArgumentException("任务结果文件超过当前存储上限");
        }
        String name = normalizeName(originalName);
        if (!name.toLowerCase(Locale.ROOT).endsWith(GENERATED_EXTENSIONS.get(contentType))) {
            throw new IllegalArgumentException("任务结果文件扩展名与内容类型不一致");
        }
        String objectKey = generatedObjectKey(purpose, contentType);
        FileObjectDraft file = new FileObjectDraft(
                objectKey, name, contentType, content.length, sha256(content), purpose, operatorId);
        storage.put(objectKey, content, contentType);
        try {
            return repository.create(file);
        } catch (RuntimeException exception) {
            try {
                storage.delete(objectKey);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    public StoredFileDownload download(String businessType, long businessId, long attachmentId) {
        StoredFileObject file = repository.findActive(businessType, businessId, attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("附件不存在或已删除"));
        return verifiedDownload(file);
    }

    public StoredFileDownload downloadGenerated(long fileId) {
        StoredFileObject file = repository.findActiveFile(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("任务结果文件不存在或已删除"));
        return verifiedDownload(file);
    }

    public void remove(String businessType, long businessId, long attachmentId, long operatorId) {
        if (!repository.softDelete(businessType, businessId, attachmentId, operatorId)) {
            throw new ResourceNotFoundException("附件不存在或已删除");
        }
    }

    private byte[] read(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("请选择需要上传的附件");
        if (upload.getSize() > properties.maxUploadBytes()) {
            throw new IllegalArgumentException("附件大小不能超过" + properties.maxUploadBytes() / 1024 / 1024 + " MiB");
        }
        try {
            byte[] content = upload.getBytes();
            if (content.length > properties.maxUploadBytes()) throw new IllegalArgumentException("附件超过大小限制");
            return content;
        } catch (IOException exception) {
            throw new FileStorageException("附件读取失败", exception);
        }
    }

    private static String normalizeName(String originalName) {
        if (originalName == null || originalName.isBlank()) throw new IllegalArgumentException("附件文件名不能为空");
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim().replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank()) throw new IllegalArgumentException("附件文件名无效");
        if (name.length() > 255) throw new IllegalArgumentException("附件文件名不能超过255个字符");
        return name;
    }

    private static String detectContentType(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) return "image/jpeg";
        if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 0x50
                && content[2] == 0x4e && content[3] == 0x47 && content[4] == 0x0d
                && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a) return "image/png";
        if (content.length >= 12 && ascii(content, 0, "RIFF") && ascii(content, 8, "WEBP")) return "image/webp";
        if (content.length >= 5 && ascii(content, 0, "%PDF-")) return "application/pdf";
        throw new IllegalArgumentException("只允许上传JPG、PNG、WEBP或PDF附件");
    }

    private static void validateExtension(String name, String contentType) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean valid = switch (contentType) {
            case "image/jpeg" -> lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            default -> lower.endsWith(UPLOAD_EXTENSIONS.get(contentType));
        };
        if (!valid) throw new IllegalArgumentException("附件扩展名与文件内容不一致");
    }

    private static boolean ascii(byte[] content, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (content[offset + index] != (byte) expected.charAt(index)) return false;
        }
        return true;
    }

    private static String objectKey(String purpose, String contentType) {
        String path = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return purpose.toLowerCase(Locale.ROOT).replace('_', '-') + "/" + path + "/"
                + UUID.randomUUID() + UPLOAD_EXTENSIONS.get(contentType);
    }

    private static String generatedObjectKey(String purpose, String contentType) {
        String path = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return "generated/" + purpose.toLowerCase(Locale.ROOT).replace('_', '-') + "/" + path + "/"
                + UUID.randomUUID() + GENERATED_EXTENSIONS.get(contentType);
    }

    private StoredFileDownload verifiedDownload(StoredFileObject file) {
        byte[] content = storage.get(file.objectKey());
        if (!sha256(content).equals(file.sha256())) {
            throw new FileStorageException("文件完整性校验失败", null);
        }
        return new StoredFileDownload(file, content);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }
}
