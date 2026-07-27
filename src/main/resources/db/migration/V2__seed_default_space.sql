INSERT INTO knowledge_dataset
    (tenant_id, name, description, chunk_strategy, parse_config, rerank_enabled, rerank_model, status)
VALUES
    ('default', 'default', 'KMA 默认数据集', '{"type":"paragraph"}', '{}', true,
     'bge-reranker-base', 'active')
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO knowledge_space
    (tenant_id, dataset_id, space_code, name, description, embedding_provider,
     embedding_model, embedding_dim, distance_metric, chunk_strategy,
     default_top_k, score_threshold, status)
SELECT 'default', dataset_id, 'default', '默认知识空间', 'KMA 本地开发默认空间',
       'local-bge-m3', 'bge-m3', 1024, 'cosine', '{"type":"paragraph"}', 5, 0.35, 'active'
FROM knowledge_dataset WHERE tenant_id = 'default' AND name = 'default'
ON CONFLICT (tenant_id, space_code) DO NOTHING;

INSERT INTO knowledge_space_acl
    (tenant_id, space_id, principal_type, principal_value, permission)
SELECT 'default', space_id, 'role', '1', 'admin'
FROM knowledge_space WHERE tenant_id = 'default' AND space_code = 'default'
ON CONFLICT DO NOTHING;
