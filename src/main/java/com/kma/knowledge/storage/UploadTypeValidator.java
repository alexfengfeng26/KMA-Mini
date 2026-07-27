package com.kma.knowledge.storage;

import cn.hutool.core.io.FileUtil;
import com.kma.common.exception.KmaException;
import com.kma.knowledge.config.KnowledgeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;

@Component
public class UploadTypeValidator {
    private final KnowledgeProperties properties;

    public UploadTypeValidator() { this(new KnowledgeProperties()); }

    @Autowired
    public UploadTypeValidator(KnowledgeProperties properties) { this.properties = properties; }
    private static final Map<String, String> TYPES = Map.ofEntries(
        Map.entry("pdf", "application/pdf"),
        Map.entry("doc", "application/msword"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xls", "application/vnd.ms-excel"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("ppt", "application/vnd.ms-powerpoint"),
        Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        Map.entry("txt", "text/plain"), Map.entry("md", "text/plain"),
        Map.entry("html", "text/html"), Map.entry("htm", "text/html"),
        Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif")
    );

    public String validate(MultipartFile file, String originalFilename) {
        String extension = FileUtil.extName(originalFilename).toLowerCase(Locale.ROOT);
        String canonical = TYPES.get(extension);
        if (canonical == null) {
            throw new KmaException(400, "不支持的文件扩展名: " + extension);
        }
        byte[] header = readHeader(file);
        if (!matchesSignature(extension, header)) {
            throw new KmaException(400, "文件内容与扩展名不匹配: " + extension);
        }
        if (extension.matches("docx|xlsx|pptx")) validateArchive(file);
        String claimed = file.getContentType();
        if (claimed != null && !claimed.isBlank() && !"application/octet-stream".equalsIgnoreCase(claimed)
            && !compatible(canonical, claimed)) {
            throw new KmaException(400, "文件 MIME 类型与扩展名不匹配");
        }
        return canonical;
    }

    private void validateArchive(MultipartFile file) {
        var limits = properties.getDocument();
        long total = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            while (zip.getNextEntry() != null) {
                if (++entries > limits.getMaxArchiveEntries()) {
                    throw new KmaException(400, "压缩文档条目数超过安全上限");
                }
                for (int read; (read = zip.read(buffer)) >= 0; ) {
                    total += Math.max(0, read);
                    if (total > limits.getMaxUncompressedBytes()) {
                        throw new KmaException(400, "压缩文档解压后大小超过安全上限");
                    }
                }
                zip.closeEntry();
            }
        } catch (KmaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new KmaException(400, "无法安全检查 Office 压缩文档");
        }
        if (entries == 0) throw new KmaException(400, "Office 压缩文档不包含有效条目");
        long compressed = Math.max(1, file.getSize());
        if (total / compressed > limits.getMaxCompressionRatio()) {
            throw new KmaException(400, "压缩文档压缩比超过安全上限");
        }
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(32);
        } catch (Exception ex) {
            throw new KmaException(400, "无法读取上传文件");
        }
    }

    private boolean matchesSignature(String extension, byte[] value) {
        if (extension.equals("pdf")) return starts(value, "%PDF".getBytes(StandardCharsets.US_ASCII));
        if (extension.matches("docx|xlsx|pptx")) return starts(value, new byte[]{0x50, 0x4b});
        if (extension.matches("doc|xls|ppt")) return starts(value,
            new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0});
        if (extension.equals("png")) return starts(value,
            new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        if (extension.matches("jpg|jpeg")) return starts(value, new byte[]{(byte) 0xff, (byte) 0xd8});
        if (extension.equals("gif")) return starts(value, "GIF8".getBytes(StandardCharsets.US_ASCII));
        return true;
    }

    private boolean starts(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }

    private boolean compatible(String canonical, String claimed) {
        return canonical.equalsIgnoreCase(claimed)
            || (canonical.equals("text/plain") && claimed.toLowerCase(Locale.ROOT).startsWith("text/"));
    }
}
