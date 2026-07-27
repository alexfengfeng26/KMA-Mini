package com.kma.knowledge.service.impl;

import com.kma.knowledge.api.KnowledgeIngestionApi;
import com.kma.knowledge.api.dto.KnowledgeFeedRequest;
import com.kma.knowledge.dto.DocIngestTextRequest;
import com.kma.knowledge.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * {@link KnowledgeIngestionApi} 实现。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeIngestionApiImpl implements KnowledgeIngestionApi {

    private final KnowledgeIngestionService ingestionService;

    @Override
    public void ingestText(KnowledgeFeedRequest request) {
        if (request == null) {
            return;
        }
        DocIngestTextRequest internal = new DocIngestTextRequest();
        internal.setSpaceCode(request.getSpaceCode());
        internal.setTitle(request.getTitle());
        internal.setSourceTag(request.getSourceTag());
        internal.setExternalRef(request.getExternalRef());
        internal.setContent(request.getContent());
        internal.setMeta(request.getMeta());
        ingestionService.ingestTextAsSystem(internal);
    }
}



