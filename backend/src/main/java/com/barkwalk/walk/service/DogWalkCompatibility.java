package com.barkwalk.walk.service;

// R3 : résultat de compatibilité chien ↔ balade (compatible ou motif précis).
public record DogWalkCompatibility(boolean compatible, String reason) {
    public static DogWalkCompatibility ok() {
        return new DogWalkCompatibility(true, null);
    }

    public static DogWalkCompatibility incompatible(String reason) {
        return new DogWalkCompatibility(false, reason);
    }
}
