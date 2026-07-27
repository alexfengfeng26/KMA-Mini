package com.kma.knowledge.service;

import com.kma.common.security.ContentSecurityService;
import com.kma.knowledge.dto.ChunkHitVO;
import com.kma.knowledge.entity.KnowledgeChunk;
import com.kma.knowledge.entity.KnowledgeDoc;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.mapper.KnowledgeChunkMapper;
import com.kma.knowledge.mapper.KnowledgeDocMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CitationSecurityServiceTest {
    @Test
    void rejectsCrossSpaceCitationAndSanitizesValidCitation() {
        KnowledgeSpaceMapper spaces=mock(KnowledgeSpaceMapper.class);KnowledgeChunkMapper chunks=mock(KnowledgeChunkMapper.class);
        KnowledgeDocMapper docs=mock(KnowledgeDocMapper.class);KnowledgeSpaceAclService acl=mock(KnowledgeSpaceAclService.class);
        ContentSecurityService content=mock(ContentSecurityService.class);
        KnowledgeSpace space=new KnowledgeSpace();space.setSpaceId(10L);space.setSpaceCode("safe");when(spaces.selectBySpaceCode("safe")).thenReturn(space);
        KnowledgeChunk chunk=new KnowledgeChunk();chunk.setChunkId(1L);chunk.setDocId(2L);chunk.setSpaceId(99L);
        KnowledgeDoc doc=new KnowledgeDoc();doc.setDocId(2L);doc.setSpaceId(99L);doc.setIsActive(true);
        when(chunks.selectByIds(any())).thenReturn(List.of(chunk));when(docs.selectByIds(any())).thenReturn(List.of(doc));
        CitationSecurityService service=new CitationSecurityService(spaces,chunks,docs,acl,content);
        ChunkHitVO hit=new ChunkHitVO();hit.setChunkId(1L);hit.setContent("secret");
        assertThatThrownBy(()->service.verifyAndSanitize("safe",List.of(hit))).isInstanceOf(AccessDeniedException.class);

        chunk.setSpaceId(10L);doc.setSpaceId(10L);
        when(content.sanitizeReference("secret","chunk:1"))
            .thenReturn(new ContentSecurityService.Inspection("[masked]",List.of("PHONE"),false));
        service.verifyAndSanitize("safe",List.of(hit));
        assertThat(hit.getContent()).isEqualTo("[masked]");verify(acl,times(2)).assertReadAccess("safe");
    }

    @Test
    void portalWideCitationGroupsBySpaceAndRejectsContentTakenOfflineAfterRetrieval() {
        KnowledgeSpaceMapper spaces=mock(KnowledgeSpaceMapper.class);KnowledgeChunkMapper chunks=mock(KnowledgeChunkMapper.class);
        KnowledgeDocMapper docs=mock(KnowledgeDocMapper.class);KnowledgeSpaceAclService acl=mock(KnowledgeSpaceAclService.class);
        ContentSecurityService content=mock(ContentSecurityService.class);
        KnowledgeSpace space=new KnowledgeSpace();space.setSpaceId(10L);space.setSpaceCode("regulations");when(spaces.selectBySpaceCode("regulations")).thenReturn(space);
        KnowledgeChunk chunk=new KnowledgeChunk();chunk.setChunkId(1L);chunk.setDocId(2L);chunk.setSpaceId(10L);
        KnowledgeDoc doc=new KnowledgeDoc();doc.setDocId(2L);doc.setSpaceId(10L);doc.setIsActive(true);doc.setPublicationManaged(true);doc.setWorkflowStatus("published");doc.setOnline(true);
        when(chunks.selectByIds(any())).thenReturn(List.of(chunk));when(docs.selectByIds(any())).thenReturn(List.of(doc));
        when(content.sanitizeReference("authority","chunk:1"))
            .thenReturn(new ContentSecurityService.Inspection("authority",List.of(),false));
        CitationSecurityService service=new CitationSecurityService(spaces,chunks,docs,acl,content);
        ChunkHitVO hit=new ChunkHitVO();hit.setChunkId(1L);hit.setSpaceCode("regulations");hit.setContent("authority");

        service.verifyAndSanitize("*",List.of(hit),true);
        verify(acl).assertReadAccess("regulations");

        doc.setOnline(false);
        assertThatThrownBy(()->service.verifyAndSanitize("*",List.of(hit),true))
            .isInstanceOf(AccessDeniedException.class);
    }
}
