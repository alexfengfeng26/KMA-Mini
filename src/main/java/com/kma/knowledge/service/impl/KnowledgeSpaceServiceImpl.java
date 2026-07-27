package com.kma.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kma.common.exception.KmaException;
import com.kma.common.security.KmaIdentityContext;
import com.kma.common.security.KmaPrincipal;
import com.kma.common.security.SecurityAuditService;
import com.kma.common.security.SpaceAdministrationGuard;
import com.kma.knowledge.dto.SpaceAclRequest;
import com.kma.knowledge.dto.SpaceCreateRequest;
import com.kma.knowledge.dto.SpaceQueryRequest;
import com.kma.knowledge.dto.SpaceUpdateRequest;
import com.kma.knowledge.dto.SpaceVO;
import com.kma.knowledge.entity.KnowledgeDataset;
import com.kma.knowledge.entity.KnowledgeSpace;
import com.kma.knowledge.entity.KnowledgeSpaceAcl;
import com.kma.knowledge.entity.ModelProfile;
import com.kma.knowledge.enums.DistanceMetric;
import com.kma.knowledge.enums.SpaceStatus;
import com.kma.knowledge.mapper.KnowledgeDatasetMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceAclMapper;
import com.kma.knowledge.mapper.KnowledgeSpaceMapper;
import com.kma.knowledge.mapper.ModelProfileMapper;
import com.kma.knowledge.service.KnowledgeSpaceService;
import com.kma.knowledge.service.KnowledgeSpaceAclService;
import com.kma.knowledge.service.AclPrincipalValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识空间服务实现
 *
 * @author party
 * @date 2026/06/30
 */
@Service
@ConditionalOnProperty(prefix = "knowledge", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeSpaceServiceImpl implements KnowledgeSpaceService {

    private final KnowledgeSpaceMapper spaceMapper;
    private final KnowledgeSpaceAclMapper aclMapper;
    private final KnowledgeDatasetMapper datasetMapper;
    private final ModelProfileMapper modelProfileMapper;
    private final KnowledgeSpaceAclService aclService;
    private final AclPrincipalValidator principalValidator;
    private final SecurityAuditService audit;
    private final SpaceAdministrationGuard administrationGuard;

    public KnowledgeSpaceServiceImpl(KnowledgeSpaceMapper spaceMapper, KnowledgeSpaceAclMapper aclMapper,
                                     KnowledgeDatasetMapper datasetMapper, ModelProfileMapper modelProfileMapper) {
        this(spaceMapper, aclMapper, datasetMapper, modelProfileMapper, null, null, null, null);
    }

    public KnowledgeSpaceServiceImpl(KnowledgeSpaceMapper spaceMapper, KnowledgeSpaceAclMapper aclMapper,
                                     KnowledgeDatasetMapper datasetMapper, ModelProfileMapper modelProfileMapper,
                                     KnowledgeSpaceAclService aclService, AclPrincipalValidator principalValidator,
                                     SecurityAuditService audit) {
        this(spaceMapper, aclMapper, datasetMapper, modelProfileMapper, aclService, principalValidator, audit, null);
    }

    @Autowired
    public KnowledgeSpaceServiceImpl(KnowledgeSpaceMapper spaceMapper, KnowledgeSpaceAclMapper aclMapper,
                                     KnowledgeDatasetMapper datasetMapper, ModelProfileMapper modelProfileMapper,
                                     KnowledgeSpaceAclService aclService, AclPrincipalValidator principalValidator,
                                     SecurityAuditService audit, SpaceAdministrationGuard administrationGuard) {
        this.spaceMapper=spaceMapper; this.aclMapper=aclMapper; this.datasetMapper=datasetMapper;
        this.modelProfileMapper=modelProfileMapper; this.aclService=aclService;
        this.principalValidator=principalValidator; this.audit=audit; this.administrationGuard=administrationGuard;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public Long create(SpaceCreateRequest request) {
        long count = spaceMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeSpace>().eq(KnowledgeSpace::getSpaceCode, request.getSpaceCode())
        );
        if (count > 0) {
            throw new KmaException("空间编码已存在");
        }
        KnowledgeDataset dataset = requireDataset(request.getDatasetId());
        validateEmbeddingBinding(dataset, request.getEmbeddingProvider(), request.getEmbeddingModel(),
            request.getEmbeddingDim());

        KnowledgeSpace space = new KnowledgeSpace();
        space.setDatasetId(request.getDatasetId());
        space.setSpaceCode(request.getSpaceCode());
        space.setName(request.getName());
        space.setDescription(request.getDescription());
        space.setEmbeddingProvider(request.getEmbeddingProvider());
        space.setEmbeddingModel(request.getEmbeddingModel());
        space.setEmbeddingDim(request.getEmbeddingDim());
        space.setDistanceMetric(StringUtils.isNotBlank(request.getDistanceMetric())
            ? request.getDistanceMetric() : DistanceMetric.COSINE.getCode());
        space.setChunkStrategy(request.getChunkStrategy());
        space.setDefaultTopK(request.getDefaultTopK() != null ? request.getDefaultTopK() : 5);
        space.setScoreThreshold(request.getScoreThreshold() != null ? request.getScoreThreshold() : new BigDecimal("0.35"));
        space.setStatus(SpaceStatus.ACTIVE.getCode());
        space.setCreateTime(LocalDateTime.now());
        space.setUpdateTime(LocalDateTime.now());
        spaceMapper.insert(space);
        KmaPrincipal principal = KmaIdentityContext.getLoginUser();
        KnowledgeSpaceAcl owner = new KnowledgeSpaceAcl();
        owner.setSpaceId(space.getSpaceId());
        boolean persistedOwner = principal != null && principal.getUserId() != null;
        if (persistedOwner && principalValidator != null) {
            try {
                principalValidator.validate("user", String.valueOf(principal.getUserId()));
            } catch (KmaException ignored) {
                persistedOwner = false;
            }
        }
        if (persistedOwner) {
            owner.setPrincipalType("user"); owner.setPrincipalValue(String.valueOf(principal.getUserId()));
        } else {
            owner.setPrincipalType("role"); owner.setPrincipalValue("kma-admin");
        }
        owner.setPermission("admin"); owner.setCreateTime(LocalDateTime.now());
        aclMapper.insert(owner);
        if (!"kma-admin".equals(owner.getPrincipalValue()) && principalValidator != null) {
            try {
                principalValidator.validate("role", "kma-admin");
                KnowledgeSpaceAcl recovery = new KnowledgeSpaceAcl();
                recovery.setSpaceId(space.getSpaceId()); recovery.setPrincipalType("role");
                recovery.setPrincipalValue("kma-admin"); recovery.setPermission("admin");
                recovery.setCreateTime(LocalDateTime.now()); aclMapper.insert(recovery);
            } catch (KmaException ignored) {
                // The creator can still own the space; the operational-admin guard below is authoritative.
            }
        }
        if (administrationGuard != null) {
            administrationGuard.assertOperationalAdmins();
        }
        record("space.create", space, Map.of("owner", owner.getPrincipalValue()));
        return space.getSpaceId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void update(SpaceUpdateRequest request) {
        KnowledgeSpace existing = spaceMapper.selectById(request.getSpaceId());
        if (existing == null) {
            throw new KmaException("空间不存在");
        }
        assertAdmin(existing);
        KnowledgeDataset dataset = requireDataset(request.getDatasetId());
        validateEmbeddingBinding(dataset, existing.getEmbeddingProvider(), request.getEmbeddingModel(),
            existing.getEmbeddingDim());

        existing.setDatasetId(request.getDatasetId());
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setEmbeddingModel(request.getEmbeddingModel());
        existing.setDistanceMetric(request.getDistanceMetric());
        existing.setChunkStrategy(request.getChunkStrategy());
        existing.setDefaultTopK(request.getDefaultTopK());
        existing.setScoreThreshold(request.getScoreThreshold());
        existing.setUpdateTime(LocalDateTime.now());
        spaceMapper.updateById(existing);
        record("space.update", existing, Map.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void delete(Long spaceId) {
        KnowledgeSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw new KmaException("空间不存在");
        }
        assertAdmin(space);
        // 清理 ACL，文档/分块由数据库级联删除
        aclMapper.delete(
            new LambdaQueryWrapper<KnowledgeSpaceAcl>().eq(KnowledgeSpaceAcl::getSpaceId, spaceId)
        );
        spaceMapper.deleteById(spaceId);
        record("space.delete", space, Map.of());
    }

    @Override
    public Page<SpaceVO> page(SpaceQueryRequest request) {
        Page<KnowledgeSpace> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<KnowledgeSpace> wrapper = new LambdaQueryWrapper<>();
        if (aclService != null) {
            Set<Long> readable = aclService.getReadableSpaceIds();
            if (readable != null && readable.isEmpty()) wrapper.eq(KnowledgeSpace::getSpaceId, -1L);
            else if (readable != null) wrapper.in(KnowledgeSpace::getSpaceId, readable);
        }
        if (StringUtils.isNotBlank(request.getSpaceCode())) {
            wrapper.eq(KnowledgeSpace::getSpaceCode, request.getSpaceCode());
        }
        if (StringUtils.isNotBlank(request.getName())) {
            wrapper.like(KnowledgeSpace::getName, request.getName());
        }
        if (StringUtils.isNotBlank(request.getStatus())) {
            wrapper.eq(KnowledgeSpace::getStatus, request.getStatus());
        }
        wrapper.orderByDesc(KnowledgeSpace::getCreateTime);
        Page<KnowledgeSpace> ApiResult = spaceMapper.selectPage(page, wrapper);

        List<SpaceVO> records = ApiResult.getRecords().stream()
            .map(this::toVo)
            .collect(Collectors.toList());
        Page<SpaceVO> voPage = new Page<>(ApiResult.getCurrent(), ApiResult.getSize(), ApiResult.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public SpaceVO getById(Long spaceId) {
        KnowledgeSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw new KmaException("空间不存在");
        }
        if (aclService != null) aclService.assertReadAccess(space.getSpaceCode());
        return toVo(space);
    }

    @Override
    public SpaceVO getBySpaceCode(String spaceCode) {
        KnowledgeSpace space = spaceMapper.selectBySpaceCode(spaceCode);
        if (space == null) {
            throw new KmaException("空间不存在");
        }
        if (aclService != null) aclService.assertReadAccess(space.getSpaceCode());
        return toVo(space);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void changeStatus(Long spaceId, String status) {
        KnowledgeSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw new KmaException("空间不存在");
        }
        assertAdmin(space);
        space.setStatus(status);
        space.setUpdateTime(LocalDateTime.now());
        spaceMapper.updateById(space);
        record("space.status.update", space, Map.of("status", status));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public Long addAcl(SpaceAclRequest request) {
        KnowledgeSpace space = spaceMapper.selectById(request.getSpaceId());
        if (space == null) {
            throw new KmaException("空间不存在");
        }
        assertAdmin(space);
        if (principalValidator != null) principalValidator.validate(request.getPrincipalType(), request.getPrincipalValue());
        long count = aclMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeSpaceAcl>()
                .eq(KnowledgeSpaceAcl::getSpaceId, request.getSpaceId())
                .eq(KnowledgeSpaceAcl::getPrincipalType, request.getPrincipalType())
                .eq(KnowledgeSpaceAcl::getPrincipalValue, request.getPrincipalValue())
                .eq(KnowledgeSpaceAcl::getPermission, request.getPermission())
        );
        if (count > 0) {
            throw new KmaException("该 ACL 已存在");
        }

        KnowledgeSpaceAcl acl = new KnowledgeSpaceAcl();
        acl.setSpaceId(request.getSpaceId());
        acl.setPrincipalType(request.getPrincipalType());
        acl.setPrincipalValue(request.getPrincipalValue());
        acl.setPermission(request.getPermission());
        acl.setCreateTime(LocalDateTime.now());
        aclMapper.insert(acl);
        record("space.acl.add", space, Map.of("principalType", request.getPrincipalType(),
            "principalValue", request.getPrincipalValue(), "permission", request.getPermission()));
        return acl.getAclId();
    }

    @Override
    public List<KnowledgeSpaceAcl> listAcls(Long spaceId) {
        KnowledgeSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw new KmaException(404, "空间不存在");
        }
        assertAdmin(space);
        return aclMapper.selectList(new LambdaQueryWrapper<KnowledgeSpaceAcl>()
            .eq(KnowledgeSpaceAcl::getSpaceId, spaceId)
            .orderByDesc(KnowledgeSpaceAcl::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "knowledgeTransactionManager")
    public void removeAcl(Long aclId) {
        KnowledgeSpaceAcl acl = aclMapper.selectById(aclId);
        if (acl == null) throw new KmaException(404, "ACL 不存在");
        KnowledgeSpace space = spaceMapper.selectById(acl.getSpaceId());
        if (space == null) throw new KmaException(404, "空间不存在");
        assertAdmin(space);
        if ("admin".equals(acl.getPermission())) {
            if (administrationGuard != null) {
                administrationGuard.assertAclRemovalAllowed(acl.getSpaceId(), aclId);
            } else {
                long admins = aclMapper.selectCount(new LambdaQueryWrapper<KnowledgeSpaceAcl>()
                    .eq(KnowledgeSpaceAcl::getSpaceId, acl.getSpaceId())
                    .eq(KnowledgeSpaceAcl::getPermission, "admin")
                    .ne(KnowledgeSpaceAcl::getAclId, aclId));
                if (admins == 0) throw new KmaException(409, "LAST_EFFECTIVE_SPACE_ADMIN_REQUIRED");
            }
        }
        aclMapper.deleteById(aclId);
        record("space.acl.remove", space, Map.of("aclId", aclId));
    }

    private SpaceVO toVo(KnowledgeSpace space) {
        SpaceVO vo = new SpaceVO();
        vo.setSpaceId(space.getSpaceId());
        vo.setDatasetId(space.getDatasetId());
        vo.setSpaceCode(space.getSpaceCode());
        vo.setName(space.getName());
        vo.setDescription(space.getDescription());
        vo.setEmbeddingProvider(space.getEmbeddingProvider());
        vo.setEmbeddingModel(space.getEmbeddingModel());
        vo.setEmbeddingDim(space.getEmbeddingDim());
        vo.setDistanceMetric(space.getDistanceMetric());
        vo.setChunkStrategy(space.getChunkStrategy());
        vo.setDefaultTopK(space.getDefaultTopK());
        vo.setScoreThreshold(space.getScoreThreshold());
        vo.setStatus(space.getStatus());
        vo.setCreateTime(space.getCreateTime());
        vo.setUpdateTime(space.getUpdateTime());
        return vo;
    }

    private KnowledgeDataset requireDataset(Long datasetId) {
        if (datasetId == null) {
            return null;
        }
        KnowledgeDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new KmaException("所属数据集不存在");
        }
        return dataset;
    }

    private void validateEmbeddingBinding(KnowledgeDataset dataset, String provider, String model, Integer dimension) {
        if (dataset == null || StringUtils.isBlank(dataset.getEmbeddingProfileCode())) {
            return;
        }
        ModelProfile profile = modelProfileMapper.selectOne(new LambdaQueryWrapper<ModelProfile>()
            .eq(ModelProfile::getProfileCode, dataset.getEmbeddingProfileCode())
            .eq(ModelProfile::getCapability, "embedding")
            .eq(ModelProfile::getEnabled, true));
        if (profile == null) {
            throw new KmaException("数据集绑定的 Embedding Profile 不可用");
        }
        if (!profile.getProvider().equals(provider) || !profile.getModelName().equals(model)
            || !profile.getDimension().equals(dimension)) {
            throw new KmaException("空间 Embedding 配置必须与数据集绑定的 Profile 一致");
        }
    }

    private void assertAdmin(KnowledgeSpace space) {
        if (aclService != null) aclService.assertAdminAccess(space.getSpaceCode());
    }

    private void record(String action, KnowledgeSpace space, Map<String, Object> details) {
        if (audit != null) audit.recordRequired("space_authorization_change", "warning", action,
            "space:" + space.getSpaceCode(), Map.of(), details, details);
    }
}



