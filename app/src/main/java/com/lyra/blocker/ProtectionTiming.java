// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

/** Clock policy: elapsed time within a boot; the saved wall deadline after a reboot. */
public final class ProtectionTiming {
    private ProtectionTiming() {}
    public static long remaining(long wallDeadline, long elapsedDeadline, int savedBoot,
                                 int currentBoot, long nowWall, long nowElapsed) {
        long delta = savedBoot == currentBoot ? elapsedDeadline - nowElapsed : wallDeadline - nowWall;
        return Math.max(0, delta);
    }
    public static long retryDelayMillis(int failures) {
        if (failures < 5) return 0;
        int step = Math.min(6, (failures - 5) / 5);
        return Math.min(1_800_000L, 30_000L * (1L << step));
    }
}
