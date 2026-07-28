package com.kma.knowledge.service;

import com.kma.common.exception.KmaException;
import com.kma.common.security.SecurityAuditService;
import com.kma.knowledge.dto.PortalThemeImmediatePublishRequest;
import com.kma.knowledge.dto.PortalVersionActionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Coordinates source synchronisation, configuration application and the shared atomic publisher. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PortalThemeReleaseService {
    private final PortalThemeService themeService;
    private final PortalSiteService siteService;
    private final SecurityAuditService auditService;

    @Transactional(transactionManager = "knowledgeTransactionManager", rollbackFor = Exception.class)
    public Map<String, Object> publishImmediately(String siteKey, String themeKey,
                                                   PortalThemeImmediatePublishRequest request) {
        Long targetVersionId = request.getThemeVersionId();
        if (request.isSyncLocalSource()) {
            Map<String, Object> synced = themeService.syncLocalSource(siteKey, themeKey);
            targetVersionId = versionId(synced);
        }
        Map<String, Object> applied = themeService.applyTheme(siteKey, targetVersionId);
        Long portalVersionId = portalVersionId(applied);
        PortalVersionActionRequest publishRequest = new PortalVersionActionRequest();
        publishRequest.setVersionId(portalVersionId);
        publishRequest.setNote(request.isSyncLocalSource()
            ? "一键直接发布：同步本地主题源码"
            : "一键直接发布：切换主题版本");
        siteService.publishImmediately(siteKey, publishRequest);
        auditService.recordRequired("portal_theme", "info", "portal-theme.publish-immediately",
            "portal-theme-version:" + targetVersionId, Map.of(), Map.of(
                "siteKey", siteKey, "themeKey", themeKey, "themeVersionId", targetVersionId,
                "localSourceSynced", request.isSyncLocalSource()), Map.of());
        return themeService.publishedWorkspace(siteKey, targetVersionId);
    }

    @SuppressWarnings("unchecked")
    private Long versionId(Map<String, Object> workspace) {
        Object version = workspace.get("version");
        if (!(version instanceof Map<?, ?> values) || !(values.get("themeVersionId") instanceof Number number))
            throw new KmaException(500, "PORTAL_THEME_WORKSPACE_INVALID");
        return number.longValue();
    }

    @SuppressWarnings("unchecked")
    private Long portalVersionId(Map<String, Object> workspace) {
        Object portalVersion = workspace.get("portalVersion");
        if (!(portalVersion instanceof Map<?, ?> values) || !(values.get("versionId") instanceof Number number))
            throw new KmaException(500, "PORTAL_THEME_WORKSPACE_INVALID");
        return number.longValue();
    }
}
