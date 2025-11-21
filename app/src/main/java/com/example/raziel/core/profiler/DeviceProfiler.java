package com.example.raziel.core.profiler;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

// Detects device capabilities for adaptive optimisation
public class DeviceProfiler {
    private static final String TAG = "DeviceProfiler";

    public enum MemoryClass {
        LOW, // < 2GB RAM available
        MEDIUM,  // 2-4GB RAM available
        HIGH, // > 4-8GB RAM available
        ULTRA  // > 8GB RAM
    }

    public enum PerformanceTier {
        ENTRY_LEVEL,  // Low-end devices focus on memory efficiency
        MID_RANGE,  // Balanced performance
        HIGH_END,  // High-end devices can use more resources
        FLAGSHIP  // Top-tier performance
    }

    public static class SegmentSize {
        public final int bytes;
        public final String description;

        public SegmentSize(int bytes, String description) {
            this.bytes = bytes;
            this.description = description;
        }

        public int bytes() {return bytes;}
    }



    private final Context context;
    private final MemoryClass memoryClass;
    private final PerformanceTier performanceTier;
    private final boolean hasAESHardware;
    private final int cpuCores;
    private final long totalMemoryMB;
    private final boolean is64Bit;


    public DeviceProfiler(Context context) {
        this.context = context.getApplicationContext();
        this.memoryClass = detectMemoryClass();
        this.performanceTier = detectPerformanceTier();
        this.hasAESHardware = detectAESHardware();
        this.cpuCores = Runtime.getRuntime().availableProcessors();
        this.totalMemoryMB = getTotalMemoryMB();
        this.is64Bit = detect64BitArchitecture();

        Log.d(TAG, String.format("Device Profile: Memory = %s (%dMB), " +
                "Tier = %s, " +
                "AES HW = %b, " +
                "CPU Cores = %d, " +
                "64-bit = %b", memoryClass, totalMemoryMB, performanceTier, hasAESHardware, cpuCores, is64Bit));
    }

    // Get total memory for classification
    private long getTotalMemoryMB() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return 1024; // conservative memory default
        }

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memoryInfo);

        return memoryInfo.totalMem / (1024 * 1024);
    }

    private MemoryClass detectMemoryClass() {
        long totalMB = getTotalMemoryMB();

        if (totalMB > 8192) { // 8GB RAM
            return MemoryClass.ULTRA;
        } else if (totalMB > 4096) { // 4-8GB RAM
            return MemoryClass.HIGH;
        } else if (totalMB > 2048) { // 2-4GB RAM
            return MemoryClass.MEDIUM;
        } else { // < 2GB RAM
            return MemoryClass.LOW;
        }
    }


    public SegmentSize getOptimalSegmentSize() {
        switch (performanceTier) {
            case FLAGSHIP:
                return new SegmentSize(1024 * 1024, "1MB - Flagship optimisation");
            case HIGH_END:
                return new SegmentSize(512 * 1024, "512KB - High performance");
            case MID_RANGE:
                return new SegmentSize(256 * 1024, "256KB - Balanced performance");
            case ENTRY_LEVEL:
            default:
                return new SegmentSize(64 * 1024, "64KB - Memory efficient");
        }
    }

    // Optimal buffer size for file I/O
    public int getOptimalBufferSize() {
        switch (performanceTier) {
            case FLAGSHIP:
                return 16 * 1024 * 1024; // 16MB - fast storage can handle large buffers
            case HIGH_END:
                return 8 * 1024 * 1024; // 8MB
            case MID_RANGE:
                return 4 * 1024 * 1024;  // 4MB
            case ENTRY_LEVEL:
            default:
                return 2 * 1024 * 1024; // 2MB - conservative for low-end devices

        }
    }

    // Optimal chunk sizes for streaming encryption
    public int getOptimalChunkSize() {
        switch (performanceTier) {
            case FLAGSHIP:
            case HIGH_END:
                return 1024 * 1024; // 1Mb chunks for high-end devices
            case MID_RANGE:
                return 512 * 1024;  // 512KB
            case ENTRY_LEVEL:
            default:
                return 256 * 1024; // 256KB for low-end devices

        }
    }


    // Optimal thread pool size for parallel processing
    public int getOptimalThreadCount() {
        int availableCores = Runtime.getRuntime().availableProcessors();

        switch (performanceTier) {
            case FLAGSHIP:
                return Math.min(availableCores, 8); // Cap at 8 threads
            case HIGH_END:
                return Math.min(availableCores, 6); // Cap at 6 threads
            case MID_RANGE:
                return Math.min(availableCores, 4); // Cap at 4 threads
            case ENTRY_LEVEL:
            default:
                return Math.min(availableCores, 2); // Cap at 2 threads for low-end
        }
    }


    // If direct buffers should be used (better performance but more memory)
    public boolean shouldUseDirectBuffers() {
        return performanceTier != PerformanceTier.ENTRY_LEVEL;
    }

    // Memory allocation strategy
    public int getMaxMemoryAllocationMB() {
        switch (memoryClass) {
            case ULTRA:
                return 512; // Can allocate up to 512MB
            case HIGH:
                return 256; // Up to 256MB
            case MEDIUM:
                return 128; // Up to 128MB
            case LOW:
            default:
                return 64; // conservative 64MB for low-end
        }
    }

    // Algorithm recommendation
    public boolean preferAES() {
        return hasAESHardware && performanceTier != PerformanceTier.ENTRY_LEVEL;
    }

    // If aggressive optimisations should be used
    public boolean enableAggressiveOptimisations() {
        return performanceTier == PerformanceTier.FLAGSHIP ||
                performanceTier == PerformanceTier.HIGH_END;
    }

    // Detect 64-bit architecture
    private boolean detect64BitArchitecture() {
        String[] abis = Build.SUPPORTED_ABIS;
        for (String abi : abis) {
            if (abi.contains("64")) {
                return true;
            }
        }
        return false;
    }

    // AES hardware detection
    private boolean detectAESHardware() {
        // Since minSDK is 26, we don't need version checks
        String[] abis = Build.SUPPORTED_ABIS;

        // Check for ARMv8 with crypto extensions
        for (String abi : abis) {
            if (abi.contains("arm64")) {
                // Most ARM64 devices have AES hardware acceleration
                return true;
            }
            if (abi.contains("x86") || abi.contains("x86_64")) {
                // x86 architectures typically have AES-NI
                return true;
            }
        }
        // Assume no hardware acceleration
        return false;
    }

    // Performance tier detection based on device capabilities
    private PerformanceTier detectPerformanceTier() {
        // Consider multiple factors: memory, cores and architecture
        int score = 0;

        // Memory score (40% weight)
        score += memoryClass.ordinal() * 40;

        // CPU cores (30% weight)
        if (cpuCores >= 8) score += 30;
        else if (cpuCores >= 6) score += 20;
        else if (cpuCores >= 4) score += 10;

        // Architecture score (30% weight)
        if (is64Bit && hasAESHardware) score += 30;
        else if (is64Bit) score += 20;
        else score += 10;

        if (score >= 90) return PerformanceTier.FLAGSHIP;
        else if (score >= 70) return PerformanceTier.HIGH_END;
        else if (score >= 50) return PerformanceTier.MID_RANGE;
        else return PerformanceTier.ENTRY_LEVEL;
    }


    // Getters
    public MemoryClass getMemoryClass() { return memoryClass; }
    public PerformanceTier getPerformanceTier() { return performanceTier; }
    public boolean hasAESHardware() { return hasAESHardware; }
    public int getCpuCores() { return cpuCores; }
    public long getTotalmemoryMB() { return totalMemoryMB; }
    public boolean isIs64Bit() { return is64Bit; }
}
