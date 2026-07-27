package com.kma.knowledge.service;

/** Resolves a secret alias without exposing the secret through model-profile APIs. */
public interface SecretProvider {
    String resolve(String alias);
}
