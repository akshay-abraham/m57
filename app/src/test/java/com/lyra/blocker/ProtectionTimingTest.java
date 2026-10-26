// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import org.junit.Test;

public class ProtectionTimingTest {
    @Test public void deadlinesAndEscalatingCooldowns() { ProtectionTimingChecks.run(); }
}
