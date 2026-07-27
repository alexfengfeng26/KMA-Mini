package com.kma.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.knowledge.dto.DocIngestFileRequest;
import com.kma.knowledge.dto.DocIngestResult;
import com.kma.knowledge.dto.DocIngestTextRequest;
import com.kma.knowledge.dto.DocQueryRequest;
import com.kma.knowledge.dto.DocVO;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库摄入服务接口
 *
 * @author party
 * @date 2026/06/30
 */
public interface KnowledgeIngestionService {

    /**
     * 纯文本摄入
     */
    DocIngestResult ingestText(DocIngestTextRequest request);

    /**
     * 可信后台任务摄入文本。仅供系统调度和跨模块内部适配器调用，不能暴露为用户接口。
     */
    DocIngestResult ingestTextAsSystem(DocIngestTextRequest request);

    /**
     * 文件摄入
     */
    DocIngestResult ingestFile(DocIngestFileRequest request, MultipartFile file);

    /**
     * 查询摄入状态
     */
    DocIngestResult getStatus(Long docId);

    /**
     * 重新索引
     */
    void reindex(Long docId);

    /**
     * 删除文档及其分块
     */
    void delete(Long docId);

    /**
     * 分页查询文档列表
     */
    Page<DocVO> page(DocQueryRequest request);

    /** 查询同一外部引用的全部版本。 */
    List<DocVO> listVersions(Long docId);
}



