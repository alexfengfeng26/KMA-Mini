package com.kma.knowledge.storage;

/** Provider-neutral object metadata used by reconciliation. */
public record StorageObjectMetadata(String location, long sizeBytes, String checksum,
                                    String checksumAlgorithm) {
}
