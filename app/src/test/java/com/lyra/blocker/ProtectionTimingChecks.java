// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

public final class ProtectionTimingChecks {
    public static void main(String[] args) { run(); System.out.println("Protection timing checks passed"); }
    public static void run() {
        // Moving the wall clock during this boot does not change a running wait.
        check(ProtectionTiming.remaining(10_000, 5_000, 7, 7, 99_000, 2_000) == 3_000, "Wall-clock jump ended a wait");
        check(ProtectionTiming.remaining(10_000, 5_000, 7, 7, 0, 2_000) == 3_000, "Wall-clock rollback extended a wait");
        check(ProtectionTiming.remaining(10_000, 5_000, 7, 8, 8_000, 100) == 2_000, "Reboot did not preserve the deadline");
        check(ProtectionTiming.remaining(10_000, 5_000, 7, 7, 8_000, 6_000) == 0, "Elapsed wait did not expire");
        check(ProtectionTiming.remaining(10_000, 5_000, 7, 8, 11_000, 100) == 0, "Rebooted expired wait remained active");
        check(ProtectionTiming.retryDelayMillis(4) == 0, "Early typo triggered a wait");
        check(ProtectionTiming.retryDelayMillis(5) == 30_000, "First cooldown incorrect");
        check(ProtectionTiming.retryDelayMillis(10) == 60_000, "Cooldown did not escalate");
        check(ProtectionTiming.retryDelayMillis(15) == 120_000, "Further cooldown did not escalate");
        check(ProtectionTiming.retryDelayMillis(1_000) == 1_800_000, "Cooldown is unbounded");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
