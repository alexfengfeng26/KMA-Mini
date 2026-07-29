package com.kma.knowledge.service;

import com.kma.knowledge.dto.ModelProfileRequest;
import com.kma.knowledge.dto.ModelProfileProbeResult;
import com.kma.knowledge.entity.ModelProfile;

import java.util.List;

public interface ModelProfileService {
    List<ModelProfile> list(String capability);
    ModelProfile create(ModelProfileRequest request);
    ModelProfile update(ModelProfileRequest request);
    ModelProfileProbeResult probe(String profileCode);
    ModelProfile activateDefault(String profileCode);
}
