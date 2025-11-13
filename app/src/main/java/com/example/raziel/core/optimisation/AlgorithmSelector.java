package com.example.raziel.core.optimisation;

import com.example.raziel.core.encryption.algorithms.InterfaceEncryptionAlgorithm;
import com.example.raziel.core.profiler.DeviceProfiler;

import java.util.List;

public class AlgorithmSelector {
    private final DeviceProfiler deviceProfiler;
    private final List<InterfaceEncryptionAlgorithm> availableAlgorithms;
    public AlgorithmSelector(DeviceProfiler deviceProfiler, List<InterfaceEncryptionAlgorithm> algorithms) {
        this.deviceProfiler = deviceProfiler;
        this.availableAlgorithms = algorithms;
    }

    /**
     * Get list of available encryption algorithms for UI display
     */
    public List<InterfaceEncryptionAlgorithm> getAvailableAlgorithms() {
        return availableAlgorithms;
    }

    /**
     * Find algorithm implementation by name
     */
    public InterfaceEncryptionAlgorithm getAlgorithmByName(String name) {
        for (InterfaceEncryptionAlgorithm algorithm : availableAlgorithms) {
            if (algorithm.getAlgorithmName().equals(name)) {
                return algorithm;
            }
        }
        return availableAlgorithms.get(1); // default software fallback
    }

    /**
     * Intelligent algorithm recommendation based on device capabilities
     */
    public InterfaceEncryptionAlgorithm getRecommendedAlgorithm() {
        return deviceProfiler.preferAES() ? getAlgorithmByName("AES-256-GCM") : getAlgorithmByName("XChaCha20-Poly1305");
    }
}
