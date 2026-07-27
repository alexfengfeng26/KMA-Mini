package com.kma.knowledge.service;

import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.storage.KnowledgeStorage;
import com.kma.knowledge.storage.StorageObjectMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalAssetService {
    private static final Set<String> TYPES = Set.of("logo", "favicon", "background", "icon", "illustration");
    private static final Set<String> MIME_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private final JdbcTemplate knowledgeJdbcTemplate;
    private final KnowledgeStorage storage;
    private final SecurityAuditService auditService;

    public List<Map<String, Object>> list(String siteKey) {
        Long siteId = siteId(siteKey);
        return knowledgeJdbcTemplate.queryForList("""
            SELECT asset_id AS "assetId",asset_key AS "assetKey",asset_type AS "assetType",
                   original_name AS "originalName",mime_type AS "mimeType",size_bytes AS "sizeBytes",
                   checksum,status,create_time AS "createTime"
            FROM knowledge_portal_asset
            WHERE site_id=? ORDER BY create_time DESC
            """, siteId);
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> upload(String siteKey, String assetType, MultipartFile file) {
        if (!TYPES.contains(assetType)) throw new KmaException(400, "PORTAL_ASSET_TYPE_INVALID");
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new KmaException(400, "PORTAL_ASSET_SIZE_INVALID");
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!MIME_TYPES.contains(mime)) throw new KmaException(400, "PORTAL_ASSET_MIME_INVALID");
        validateSignature(file, mime);
        Long siteId = siteId(siteKey);
        String assetKey = assetType + "-" + UUID.randomUUID().toString().replace("-", "");
        String location;
        try {
            location = storage.store("portal-" + siteKey,
                file.getOriginalFilename() == null ? assetKey + ".bin" : file.getOriginalFilename(),
                file.getInputStream());
            StorageObjectMetadata metadata = storage.inspect(location);
            Long assetId = knowledgeJdbcTemplate.queryForObject("""
                INSERT INTO knowledge_portal_asset
                  (site_id,asset_key,asset_type,original_name,mime_type,storage_path,
                   size_bytes,checksum,created_by)
                VALUES (?,?,?,?,?,?,?,?,?) RETURNING asset_id
                """, Long.class, siteId, assetKey, assetType,
                file.getOriginalFilename() == null ? assetKey : file.getOriginalFilename(), mime, location,
                metadata.sizeBytes(), metadata.checksum(), KmaIdentityContext.getUserId());
            auditService.recordRequired("portal_configuration", "info", "portal-asset.upload",
                "portal-asset:" + assetId, Map.of(), Map.of(
                    "siteKey", siteKey, "assetKey", assetKey, "mimeType", mime), Map.of());
            return Map.of("assetId", assetId, "assetKey", assetKey, "assetType", assetType,
                "url", "/api/v1/portal-sites/" + siteKey + "/assets/" + assetKey,
                "sizeBytes", metadata.sizeBytes(), "checksum", metadata.checksum());
        } catch (IOException ex) {
            throw new KmaException("PORTAL_ASSET_STORAGE_FAILED", ex);
        }
    }

    public Asset open(String siteKey, String assetKey) {
        List<Asset> rows = knowledgeJdbcTemplate.query("""
            SELECT a.storage_path,a.mime_type,a.original_name
            FROM knowledge_portal_asset a JOIN knowledge_portal_site s
              ON s.site_id=a.site_id
            WHERE s.site_key=? AND s.status='active' AND a.asset_key=? AND a.status='active'
            """, (rs, rowNum) -> new Asset(rs.getString("storage_path"), rs.getString("mime_type"),
            rs.getString("original_name")), siteKey, assetKey);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_ASSET_NOT_FOUND");
        return rows.getFirst();
    }

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public void delete(String siteKey, Long assetId) {
        Long siteId = siteId(siteKey);
        List<Map<String, Object>> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT asset_key AS "assetKey",storage_path AS "storagePath"
            FROM knowledge_portal_asset WHERE site_id=? AND asset_id=?
            """, siteId, assetId);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_ASSET_NOT_FOUND");
        String assetKey = String.valueOf(rows.getFirst().get("assetKey"));
        Integer referenced = knowledgeJdbcTemplate.queryForObject("""
            SELECT count(*) FROM knowledge_portal_config_version
            WHERE site_id=? AND status='published' AND config_json::text LIKE ?
            """, Integer.class, siteId, "%" + assetKey + "%");
        if (referenced != null && referenced > 0) throw new KmaException(409, "PORTAL_ASSET_IN_USE");
        auditService.recordRequired("portal_configuration", "warning", "portal-asset.delete",
            "portal-asset:" + assetId, Map.of("siteKey", siteKey, "assetKey", assetKey), Map.of(), Map.of());
        knowledgeJdbcTemplate.update("""
            DELETE FROM knowledge_portal_asset WHERE site_id=? AND asset_id=?
            """, siteId, assetId);
        try {
            storage.delete(String.valueOf(rows.getFirst().get("storagePath")));
        } catch (IOException ex) {
            throw new KmaException("PORTAL_ASSET_DELETE_FAILED", ex);
        }
    }

    public InputStream stream(Asset asset) throws IOException {
        return storage.open(asset.location());
    }

    private Long siteId(String siteKey) {
        List<Long> rows = knowledgeJdbcTemplate.queryForList("""
            SELECT site_id FROM knowledge_portal_site WHERE site_key=?
            """, Long.class, siteKey);
        if (rows.isEmpty()) throw new KmaException(404, "PORTAL_SITE_NOT_FOUND");
        return rows.getFirst();
    }

    private void validateSignature(MultipartFile file, String mime) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            boolean valid = switch (mime) {
                case "image/png" -> starts(header, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
                case "image/jpeg" -> starts(header, new byte[] {(byte) 0xff, (byte) 0xd8});
                case "image/gif" -> starts(header, "GIF8".getBytes());
                case "image/webp" -> header.length >= 12 && new String(header).startsWith("RIFF")
                    && new String(header, 8, 4).equals("WEBP");
                default -> false;
            };
            if (!valid) throw new KmaException(400, "PORTAL_ASSET_SIGNATURE_INVALID");
        } catch (IOException ex) {
            throw new KmaException(400, "PORTAL_ASSET_READ_FAILED");
        }
    }

    private boolean starts(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++)
            if (value[index] != prefix[index]) return false;
        return true;
    }

    public record Asset(String location, String mimeType, String originalName) {}
}
