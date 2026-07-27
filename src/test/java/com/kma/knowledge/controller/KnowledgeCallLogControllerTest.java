package com.kma.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.common.result.ApiResult;
import com.kma.knowledge.dto.CallLogQueryRequest;
import com.kma.knowledge.dto.KnowledgeCallLogVO;
import com.kma.knowledge.service.KnowledgeCallLogService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

class KnowledgeCallLogControllerTest {

    @Test
    void pageShouldWrapPageData() {
        KnowledgeCallLogService service = Mockito.mock(KnowledgeCallLogService.class);
        KnowledgeCallLogController controller = new KnowledgeCallLogController(service);

        KnowledgeCallLogVO vo = new KnowledgeCallLogVO();
        vo.setLogId(1L);
        vo.setSpaceCode("meeting_minutes");
        Page<KnowledgeCallLogVO> page = new Page<>(2, 20, 1);
        page.setRecords(java.util.List.of(vo));
        Mockito.when(service.page(Mockito.any(CallLogQueryRequest.class))).thenReturn(page);

        ApiResult<Map<String, Object>> ApiResult = controller.page(new CallLogQueryRequest());
        Assertions.assertEquals(200, ApiResult.getCode());
        Assertions.assertEquals(1L, ApiResult.getData().get("total"));
        Assertions.assertEquals(2L, ApiResult.getData().get("pageNum"));
    }

    @Test
    void getByIdShouldReturnDetail() {
        KnowledgeCallLogService service = Mockito.mock(KnowledgeCallLogService.class);
        KnowledgeCallLogController controller = new KnowledgeCallLogController(service);

        KnowledgeCallLogVO vo = new KnowledgeCallLogVO();
        vo.setLogId(8L);
        vo.setStatus("success");
        Mockito.when(service.getById(8L)).thenReturn(vo);

        ApiResult<KnowledgeCallLogVO> ApiResult = controller.getById(8L);
        Assertions.assertEquals(200, ApiResult.getCode());
        Assertions.assertEquals(8L, ApiResult.getData().getLogId());
    }
}



