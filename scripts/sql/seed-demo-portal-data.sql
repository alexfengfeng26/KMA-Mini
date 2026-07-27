\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
  IF current_database() <> 'kma_mini' THEN
    RAISE EXCEPTION 'DEMO_PORTAL_SEED_TARGET_MUST_BE_KMA_MINI';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM knowledge_space WHERE space_code = 'default' AND status = 'active') THEN
    RAISE EXCEPTION 'DEMO_PORTAL_SEED_DEFAULT_SPACE_REQUIRED';
  END IF;
  IF EXISTS (SELECT 1 FROM knowledge_doc WHERE source_tag IS DISTINCT FROM 'demo-portal') THEN
    RAISE EXCEPTION 'DEMO_PORTAL_SEED_NON_DEMO_DOCUMENTS_PRESENT';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM kma_user WHERE username = 'admin' AND status = 'active') THEN
    RAISE EXCEPTION 'DEMO_PORTAL_SEED_ADMIN_REQUIRED';
  END IF;
END $$;

WITH topic_style(topic_code, cover_color, icon, display_mode, sort_order) AS (
  VALUES
    ('party_constitution', '#A53535', 'DocumentChecked', 'timeline', 10),
    ('policy', '#1E6B9B', 'Reading', 'list', 20),
    ('learning_material', '#287D5A', 'Notebook', 'cards', 30),
    ('grassroots_case', '#B06A1D', 'Connection', 'cards', 40),
    ('organization_system', '#6A4CA1', 'Management', 'faq', 50)
)
UPDATE knowledge_topic t
SET cover_color = s.cover_color,
    icon = s.icon,
    display_mode = s.display_mode,
    sort_order = s.sort_order,
    topic_type = 'topic',
    enabled = TRUE,
    featured = TRUE,
    update_time = now()
FROM topic_style s
WHERE t.topic_code = s.topic_code;

CREATE TEMP TABLE demo_portal_document (
  demo_key text PRIMARY KEY,
  title text NOT NULL,
  content_type text NOT NULL,
  document_number text NOT NULL,
  issuing_authority text NOT NULL,
  publish_date date NOT NULL,
  effective_date date,
  expiry_date date,
  validity_status text NOT NULL,
  workflow_status text NOT NULL,
  review_decision text,
  online boolean NOT NULL,
  is_active boolean NOT NULL,
  summary text NOT NULL,
  keywords jsonb NOT NULL,
  topic_code text NOT NULL,
  body text NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_portal_document VALUES
('constitution-01','党支部年度组织生活工作指引（演示）','party_constitution','演示党办〔2026〕1号','示范单位党委办公室','2026-01-06','2026-01-06',NULL,'effective','published','approved',TRUE,TRUE,'用于规范年度组织生活计划、台账留存和问题闭环的演示指引。','["组织生活","年度计划","台账"]','party_constitution','本材料为演示文本。党支部应在年初形成组织生活年度计划，明确主题党日、党员大会和谈心谈话安排。计划执行中出现调整时，应记录调整原因、责任人和完成时限，并在季度末开展自查。'),
('constitution-02','党员权利义务学习要点（演示）','party_constitution','演示组宣〔2026〕2号','示范单位组织部','2026-01-15','2026-01-15',NULL,'effective','published','approved',TRUE,TRUE,'以问答式语言梳理党员教育、服务群众和组织纪律等学习要点。','["党员义务","学习要点","组织纪律"]','party_constitution','本材料为演示文本。党员应主动参加组织生活和理论学习，如实向组织报告有关事项。支部在开展教育时，应将权利保障、纪律要求和服务承诺一并纳入学习清单，避免只布置不反馈。'),
('constitution-03','支部换届准备工作清单（演示）','party_constitution','演示组通〔2026〕3号','示范单位组织部','2026-02-02','2026-02-02',NULL,'effective','published','approved',TRUE,TRUE,'覆盖换届前调研、候选人酝酿、会议材料与归档的模拟清单。','["支部换届","候选人","会议材料"]','party_constitution','本材料为演示文本。换届准备应提前开展党员和群众意见征集，核对组织关系和应到会人数。会议材料由专人校核，选举结束后将纪要、票据和结果报告按目录归档。'),
('constitution-draft','党小组学习记录规范（演示草稿）','party_constitution','演示组稿〔2026〕4号','示范单位组织部','2026-07-10','2026-07-10',NULL,'effective','draft',NULL,FALSE,FALSE,'待完善的党小组学习记录模板，供治理后台草稿状态展示。','["党小组","学习记录","草稿"]','party_constitution','本材料为演示文本和草稿状态样例。记录应包含学习主题、参加人员、交流摘要和后续事项。当前版本仍待补充责任分工与归档周期说明。'),
('policy-01','重点任务闭环督办办法（演示）','policy','演示督办〔2026〕5号','示范单位党委办公室','2026-01-20','2026-02-01',NULL,'effective','published','approved',TRUE,TRUE,'模拟党委重点任务从立项、交办、催办到销号的闭环管理制度。','["督办","闭环管理","重点任务"]','policy','本材料为演示文本。重点任务建立一事一档，交办时明确主责部门、协同部门和完成节点。临近期限未完成的事项进入黄色提醒，逾期事项提交专题会研判并形成销号依据。'),
('policy-02','基层减负事项报送规范（演示）','policy','演示办发〔2026〕6号','示范单位党委办公室','2026-02-08','2026-02-15',NULL,'effective','published','approved',TRUE,TRUE,'以清单化方式模拟报表归口、频次控制与重复材料整合要求。','["基层减负","报送","清单管理"]','policy','本材料为演示文本。面向基层的材料报送应实行归口管理，同类报表原则上合并填报。临时新增事项需说明必要性和替代方案，避免多头重复取数。'),
('policy-03','群众诉求办理回访机制（演示）','policy','演示群工〔2026〕7号','示范单位群众工作部','2026-02-18','2026-03-01',NULL,'effective','published','approved',TRUE,TRUE,'模拟群众诉求受理、分办、办理、回访和评价的服务闭环。','["群众诉求","回访","服务闭环"]','policy','本材料为演示文本。群众诉求受理后应在一个工作日内明确承办责任，复杂事项可分阶段反馈。办结后通过电话或现场回访核验结果，并将共性问题纳入季度分析。'),
('policy-04','安全生产党员责任区实施细则（演示）','policy','演示安委〔2026〕8号','示范单位安全委员会','2026-03-06','2026-03-10',NULL,'effective','published','approved',TRUE,TRUE,'模拟党员责任区在风险巡查、隐患上报和整改复核中的职责。','["安全生产","党员责任区","隐患整改"]','policy','本材料为演示文本。党员责任区应覆盖重点风险点位，责任人每周开展巡查并形成台账。发现隐患后及时上报，整改完成后由不同人员复核，确保问题真正闭环。'),
('policy-05','年度培训经费使用指引（演示，待生效）','policy','演示财教〔2026〕9号','示范单位财务部','2026-07-01','2026-08-01',NULL,'pending','published','approved',TRUE,TRUE,'已发布、将在下月生效的培训经费使用演示指引。','["培训经费","待生效","预算"]','policy','本材料为演示文本。培训经费应围绕年度重点任务编制预算，按规定完成审批和报销。该指引尚未到生效日期，门户可通过效力状态筛选查看。'),
('policy-06','档案借阅管理办法（演示，已失效）','policy','演示档案〔2024〕10号','示范单位综合管理部','2024-02-01','2024-03-01','2026-06-30','expired','published','approved',TRUE,TRUE,'已失效的历史办法，用于验证门户历史效力筛选。','["档案借阅","已失效","历史资料"]','policy','本材料为演示文本。该办法已于二零二六年六月三十日停止适用，仅保留作历史查询和制度沿革展示，不作为当前业务办理依据。'),
('policy-draft','会议费用管理提示（演示，驳回待修订）','policy','演示财务〔2026〕11号','示范单位财务部','2026-07-12','2026-07-12',NULL,'effective','draft','rejected',FALSE,FALSE,'审核驳回后的修订样例，展示治理后台待完善状态。','["会议费用","驳回","修订"]','policy','本材料为演示文本和驳回样例。当前版本缺少费用标准引用和审批节点说明，审核意见要求补充依据后重新提交。'),
('learning-01','青年理论学习小组月度学习方案（演示）','learning_material','演示学组〔2026〕12号','示范单位团委','2026-01-12','2026-01-12',NULL,'effective','published','approved',TRUE,TRUE,'模拟青年理论学习小组的主题、领学、研讨与成果转化安排。','["青年理论学习","月度计划","研讨"]','learning_material','本材料为演示文本。学习小组每月围绕一个主题开展领学、交流和实践转化，成员需结合岗位提交一项改进建议。组织者在月底汇总学习成效和未完成事项。'),
('learning-02','主题党日主持词参考框架（演示）','learning_material','演示宣教〔2026〕13号','示范单位宣传部','2026-02-14','2026-02-14',NULL,'effective','published','approved',TRUE,TRUE,'为主题党日提供议程串联、互动交流和承诺践诺的演示模板。','["主题党日","主持词","承诺践诺"]','learning_material','本材料为演示文本。主题党日主持应突出学习主题和问题导向，安排党员交流岗位体会。活动结尾形成可追踪的承诺事项，并在下次活动前通报落实情况。'),
('learning-03','岗位建功案例复盘方法（演示）','learning_material','演示培训〔2026〕14号','示范单位培训中心','2026-03-12','2026-03-12',NULL,'effective','published','approved',TRUE,TRUE,'模拟将岗位实践沉淀为可复用学习案例的复盘方法。','["岗位建功","案例复盘","经验萃取"]','learning_material','本材料为演示文本。案例复盘采用背景、行动、结果、反思四步法，重点说明关键决策和协同方式。整理后的案例应避免个人敏感信息，并给出可复制的操作建议。'),
('learning-04','数字化办公基础训练营课程表（演示，待生效）','learning_material','演示培训〔2026〕15号','示范单位培训中心','2026-07-05','2026-08-15',NULL,'pending','published','approved',TRUE,TRUE,'待生效的数字化办公课程安排，用于展示未来培训计划。','["数字化办公","训练营","待生效"]','learning_material','本材料为演示文本。训练营设置文档协作、数据整理和知识检索三个模块，学员完成练习后由导师点评。课程将在生效日期后正式开放报名。'),
('learning-review','基层宣讲骨干培养方案（演示，审核中）','learning_material','演示宣教〔2026〕16号','示范单位宣传部','2026-07-08','2026-07-08',NULL,'effective','reviewing','pending',FALSE,FALSE,'已提交审核的宣讲骨干培养方案，供审核列表展示。','["基层宣讲","审核中","骨干培养"]','learning_material','本材料为演示文本和审核中样例。培养方案包含课程学习、试讲评议和跟岗实践三个阶段，当前正在等待审核意见。'),
('case-01','窗口服务党员先锋岗实践案例（演示）','grassroots_case','演示案例〔2026〕17号','示范单位客户服务中心','2026-01-28','2026-01-28',NULL,'effective','published','approved',TRUE,TRUE,'模拟窗口服务场景中党员先锋岗提升响应速度与满意度的案例。','["先锋岗","窗口服务","群众满意度"]','grassroots_case','本材料为演示文本。窗口服务团队设置党员先锋岗，针对高频问题形成一次告知清单。通过每日复盘排队时长和群众评价，逐步优化导办与分流安排。'),
('case-02','项目攻坚临时党小组协同案例（演示）','grassroots_case','演示案例〔2026〕18号','示范单位项目管理部','2026-02-25','2026-02-25',NULL,'effective','published','approved',TRUE,TRUE,'模拟跨部门项目攻坚中临时党小组的协同与风险协调机制。','["项目攻坚","临时党小组","协同"]','grassroots_case','本材料为演示文本。项目攻坚期间成立临时党小组，围绕进度、质量和安全建立周例会机制。对跨部门堵点实行党员认领和清单销号，推动问题快速解决。'),
('case-03','社区共建志愿服务项目案例（演示）','grassroots_case','演示案例〔2026〕19号','示范单位党群工作部','2026-03-20','2026-03-20',NULL,'effective','published','approved',TRUE,TRUE,'模拟单位党组织与社区共建开展便民服务的项目复盘。','["社区共建","志愿服务","项目复盘"]','grassroots_case','本材料为演示文本。共建项目通过走访收集老年人办事难点，设置周末志愿服务时段。项目结束后以服务人次、问题解决率和居民反馈评估成效。'),
('case-review','一线班组微创新实践案例（演示，审核中）','grassroots_case','演示案例〔2026〕20号','示范单位生产运营部','2026-07-09','2026-07-09',NULL,'effective','reviewing','approved',FALSE,FALSE,'已审核通过、待发布的一线班组微创新案例。','["班组创新","待发布","审核通过"]','grassroots_case','本材料为演示文本和待发布样例。一线班组围绕设备点检提出微创新方案，当前已完成审核，等待内容管理员执行发布。'),
('case-draft','群众意见台账分析案例（演示草稿）','grassroots_case','演示案例〔2026〕21号','示范单位党群工作部','2026-07-13','2026-07-13',NULL,'effective','draft',NULL,FALSE,FALSE,'未提交的群众意见分析案例，用于草稿筛选展示。','["群众意见","台账分析","草稿"]','grassroots_case','本材料为演示文本和草稿状态样例。案例拟通过诉求台账识别重复问题，目前正在补充数据来源说明和后续改进措施。'),
('org-01','党支部委员分工与履职清单（演示）','organization_system','演示组织〔2026〕22号','示范单位组织部','2026-01-18','2026-01-18',NULL,'effective','published','approved',TRUE,TRUE,'模拟党支部委员岗位职责、协同事项和年度述职要求。','["支部委员","履职清单","述职"]','organization_system','本材料为演示文本。支部委员应依据分工认领党建、宣传、纪检和群众工作事项。每季度通报履职进展，年度述职重点说明问题整改和服务群众成效。'),
('org-02','党员发展对象培养考察流程（演示）','organization_system','演示组织〔2026〕23号','示范单位组织部','2026-02-22','2026-02-22',NULL,'effective','published','approved',TRUE,TRUE,'模拟发展对象教育培养、谈话考察与材料审核的流程规范。','["党员发展","培养考察","流程"]','organization_system','本材料为演示文本。发展对象培养应明确联系人，定期记录思想汇报和实践表现。组织部门在提交支部讨论前核对培养期限、谈话记录和政审材料。'),
('org-03','组织生活会问题整改跟踪表（演示）','organization_system','演示组织〔2026〕24号','示范单位组织部','2026-03-25','2026-03-25',NULL,'effective','published','approved',TRUE,TRUE,'模拟组织生活会后问题整改的责任、节点和效果评估台账。','["组织生活会","问题整改","跟踪表"]','organization_system','本材料为演示文本。整改台账应逐项明确问题表现、责任人、完成时限和验证方式。支委会定期研究进展，对未完成事项说明原因并调整措施。'),
('org-review','党费收缴提醒服务规范（演示，审核中）','organization_system','演示组织〔2026〕25号','示范单位组织部','2026-07-11','2026-07-11',NULL,'effective','reviewing','pending',FALSE,FALSE,'等待审核的党费提醒服务规范，展示待审核业务状态。','["党费收缴","提醒服务","审核中"]','organization_system','本材料为演示文本和审核中样例。提醒服务应保护个人信息，按月推送缴纳提示并保留异常处理记录，当前版本正在审核。');

INSERT INTO knowledge_doc (
  space_id,title,source_tag,external_ref,source_version,mime_type,content_hash,parse_status,chunk_count,
  meta,is_active,storage_size_bytes,publication_managed,content_type,document_number,issuing_authority,
  publish_date,effective_date,expiry_date,validity_status,workflow_status,review_decision,review_note,online,
  summary,keywords,reviewer_id,submitted_at,reviewed_at,published_at,activated_at,create_time,update_time
)
SELECT s.space_id,d.title,'demo-portal','demo:portal:' || d.demo_key,1,'text/plain',md5(d.demo_key),'completed',2,
  jsonb_build_object('demo',true,'disclaimer','拟真虚构演示数据，不作为正式制度依据','seedKey',d.demo_key),
  d.is_active,0,TRUE,d.content_type,d.document_number,d.issuing_authority,d.publish_date,d.effective_date,d.expiry_date,
  d.validity_status,d.workflow_status,d.review_decision,
  CASE WHEN d.review_decision='rejected' THEN '演示审核意见：请补充依据后重新提交。' WHEN d.workflow_status='reviewing' THEN '演示：等待内容审核。' ELSE NULL END,
  d.online,d.summary,d.keywords,u.user_id,
  CASE WHEN d.workflow_status IN ('reviewing','published') THEN now() - interval '5 days' ELSE NULL END,
  CASE WHEN d.review_decision IN ('approved','rejected') THEN now() - interval '3 days' ELSE NULL END,
  CASE WHEN d.workflow_status='published' THEN now() - interval '2 days' ELSE NULL END,
  CASE WHEN d.is_active THEN now() - interval '2 days' ELSE NULL END,
  now() - interval '14 days',now()
FROM demo_portal_document d
CROSS JOIN (SELECT space_id FROM knowledge_space WHERE space_code='default') s
CROSS JOIN (SELECT user_id FROM kma_user WHERE username='admin') u
ON CONFLICT (space_id,external_ref,source_version) WHERE external_ref IS NOT NULL
DO UPDATE SET
  title=EXCLUDED.title, mime_type=EXCLUDED.mime_type, content_hash=EXCLUDED.content_hash,
  parse_status=EXCLUDED.parse_status, chunk_count=EXCLUDED.chunk_count, meta=EXCLUDED.meta,
  is_active=EXCLUDED.is_active, publication_managed=EXCLUDED.publication_managed,
  content_type=EXCLUDED.content_type, document_number=EXCLUDED.document_number,
  issuing_authority=EXCLUDED.issuing_authority, publish_date=EXCLUDED.publish_date,
  effective_date=EXCLUDED.effective_date, expiry_date=EXCLUDED.expiry_date,
  validity_status=EXCLUDED.validity_status, workflow_status=EXCLUDED.workflow_status,
  review_decision=EXCLUDED.review_decision, review_note=EXCLUDED.review_note, online=EXCLUDED.online,
  summary=EXCLUDED.summary, keywords=EXCLUDED.keywords, reviewer_id=EXCLUDED.reviewer_id,
  submitted_at=EXCLUDED.submitted_at, reviewed_at=EXCLUDED.reviewed_at, published_at=EXCLUDED.published_at,
  activated_at=EXCLUDED.activated_at, update_time=now();

DELETE FROM knowledge_doc_topic dt
USING knowledge_doc d
WHERE dt.doc_id=d.doc_id AND d.source_tag='demo-portal';

INSERT INTO knowledge_doc_topic(doc_id,topic_id)
SELECT d.doc_id,t.topic_id
FROM knowledge_doc d
JOIN demo_portal_document seed ON d.external_ref='demo:portal:' || seed.demo_key
JOIN knowledge_topic t ON t.topic_code=seed.topic_code;

DELETE FROM knowledge_chunk c
USING knowledge_doc d
WHERE c.doc_id=d.doc_id AND d.source_tag='demo-portal';

INSERT INTO knowledge_chunk(doc_id,space_id,chunk_index,content,char_offset,token_count,source_tag,search_text,meta)
SELECT d.doc_id,d.space_id,part.chunk_index,part.content,part.char_offset,length(part.content),'demo-portal',lexical.analyzed_search_text,
  jsonb_build_object('demo',true,'seedKey',seed.demo_key,'part',part.chunk_index)
FROM knowledge_doc d
JOIN demo_portal_document seed ON d.external_ref='demo:portal:' || seed.demo_key
CROSS JOIN LATERAL (
  VALUES
    (0, 0, seed.summary || ' 关键词：' || array_to_string(ARRAY(SELECT jsonb_array_elements_text(seed.keywords)), '、') || '。'),
    (1, length(seed.summary) + 1, seed.body || ' 本内容仅用于 KMA Mini 门户业务流程和检索演示。')
) AS part(chunk_index,char_offset,content)
CROSS JOIN LATERAL (
  SELECT part.content || ' ' || COALESCE((
    SELECT string_agg(substring(part.content FROM position FOR 2), ' ')
    FROM generate_series(1, GREATEST(char_length(part.content) - 1, 0)) AS position
    WHERE substring(part.content FROM position FOR 1) ~ '[一-龥]'
      AND substring(part.content FROM position + 1 FOR 1) ~ '[一-龥]'
  ), '') AS analyzed_search_text
) AS lexical;

DELETE FROM knowledge_favorite f
USING knowledge_doc d
WHERE f.doc_id=d.doc_id AND d.source_tag='demo-portal' AND f.favorite_type='content';

INSERT INTO knowledge_favorite(user_id,favorite_type,doc_id,title,create_time)
SELECT u.user_id,'content',d.doc_id,d.title,now() - (v.position || ' hours')::interval
FROM (VALUES ('policy-01',1),('learning-02',2),('case-01',3)) v(demo_key,position)
JOIN knowledge_doc d ON d.external_ref='demo:portal:' || v.demo_key
CROSS JOIN (SELECT user_id FROM kma_user WHERE username='admin') u
ON CONFLICT (user_id,doc_id) WHERE favorite_type='content'
DO UPDATE SET title=EXCLUDED.title,create_time=EXCLUDED.create_time;

DELETE FROM knowledge_read_history h
USING knowledge_doc d
WHERE h.doc_id=d.doc_id AND d.source_tag='demo-portal';

INSERT INTO knowledge_read_history(user_id,doc_id,last_location,last_read_at,read_count)
SELECT u.user_id,d.doc_id,'section-' || v.position,now() - (v.position || ' hours')::interval,2 + v.position
FROM (VALUES ('constitution-01',1),('policy-03',2),('learning-01',3),('org-02',4)) v(demo_key,position)
JOIN knowledge_doc d ON d.external_ref='demo:portal:' || v.demo_key
CROSS JOIN (SELECT user_id FROM kma_user WHERE username='admin') u
ON CONFLICT (user_id,doc_id)
DO UPDATE SET last_location=EXCLUDED.last_location,last_read_at=EXCLUDED.last_read_at,read_count=EXCLUDED.read_count;

DO $$
DECLARE
  doc_total integer;
  chunk_total integer;
  topic_total integer;
  favorite_total integer;
  history_total integer;
  thematic_hits integer;
BEGIN
  SELECT count(*) INTO doc_total FROM knowledge_doc WHERE source_tag='demo-portal';
  SELECT count(*) INTO chunk_total FROM knowledge_chunk c JOIN knowledge_doc d ON d.doc_id=c.doc_id WHERE d.source_tag='demo-portal';
  SELECT count(DISTINCT t.topic_code) INTO topic_total FROM knowledge_doc_topic dt JOIN knowledge_doc d ON d.doc_id=dt.doc_id JOIN knowledge_topic t ON t.topic_id=dt.topic_id WHERE d.source_tag='demo-portal';
  SELECT count(*) INTO favorite_total FROM knowledge_favorite f JOIN knowledge_doc d ON d.doc_id=f.doc_id WHERE d.source_tag='demo-portal';
  SELECT count(*) INTO history_total FROM knowledge_read_history h JOIN knowledge_doc d ON d.doc_id=h.doc_id WHERE d.source_tag='demo-portal';
  SELECT count(*) INTO thematic_hits
  FROM knowledge_chunk c
  JOIN knowledge_doc d ON d.doc_id=c.doc_id
  WHERE d.source_tag='demo-portal'
    AND d.workflow_status='published' AND d.online AND d.is_active
    AND c.search_vector @@ to_tsquery('simple', '主题 | 题党 | 党日');
  IF doc_total <> 25 OR chunk_total <> 50 OR topic_total <> 5 OR favorite_total <> 3 OR history_total <> 4 THEN
    RAISE EXCEPTION 'DEMO_PORTAL_SEED_VERIFICATION_FAILED docs=% chunks=% topics=% favorites=% history=%',doc_total,chunk_total,topic_total,favorite_total,history_total;
  END IF;
  IF thematic_hits = 0 THEN
    RAISE EXCEPTION 'DEMO_PORTAL_SEED_LEXICAL_VERIFICATION_FAILED';
  END IF;
END $$;

COMMIT;
