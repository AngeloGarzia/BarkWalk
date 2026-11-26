package com.barkwalk.storage;

/** Image persistée localement après validation de la signature binaire. */
public record StoredImage(String storageKey, String contentType) {}
