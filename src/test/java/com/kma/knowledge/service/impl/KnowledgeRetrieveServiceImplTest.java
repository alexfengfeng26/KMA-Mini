package com.kma.knowledge.service.impl;

import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.dto.RetrieveRequest;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.rag.retrieve.HybridRetriever;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetrieveServiceImplTest {
    @Test
    void portalAllFansOutOnlyToReadableSpacesAndAppliesOneGlobalTopK() {
        HybridRetriever retriever=mock(HybridRetriever.class);
        KnowledgeSpaceAclService acl=mock(KnowledgeSpaceAclService.class);
        KnowledgeRetrieveServiceImpl service=new KnowledgeRetrieveServiceImpl(retriever,acl);
        KnowledgeSpace regulations=space(1L,"regulations"),cases=space(2L,"cases");
        when(acl.getReadableSpaces()).thenReturn(List.of(regulations,cases));
        when(retriever.retrieve(any(RetrieveRequest.class))).thenAnswer(invocation -> {
            RetrieveRequest scoped=invocation.getArgument(0);
            return "regulations".equals(scoped.getSpaceCode())
                ? List.of(hit(11L,"regulations",0.7))
                : List.of(hit(22L,"cases",0.9),hit(23L,"cases",0.6));
        });
        RetrieveRequest request=new RetrieveRequest();request.setQuery("组织生活");request.setSpaceCode("*");request.setPortalOnly(true);request.setTopK(2);

        List<ChunkHitVO> hits=service.retrieveChunks(request);

        assertThat(hits).extracting(ChunkHitVO::getChunkId).containsExactly(22L,11L);
        verify(retriever).retrieve(argThat(r->"regulations".equals(r.getSpaceCode())&&Boolean.TRUE.equals(r.getPortalOnly())));
        verify(retriever).retrieve(argThat(r->"cases".equals(r.getSpaceCode())&&Boolean.TRUE.equals(r.getPortalOnly())));
    }

    private KnowledgeSpace space(Long id,String code){KnowledgeSpace s=new KnowledgeSpace();s.setSpaceId(id);s.setSpaceCode(code);s.setStatus("active");return s;}
    private ChunkHitVO hit(Long id,String space,double score){ChunkHitVO h=new ChunkHitVO();h.setChunkId(id);h.setSpaceCode(space);h.setScore(score);return h;}
}
