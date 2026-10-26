// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import org.junit.Test;

public class FamilyRulesTest {
    @Test public void hostsAndBrowserRules() throws Exception { FamilyRuleChecks.run(); }
    @Test public void dnsPacketsAndChecksums() throws Exception { DnsPacketChecks.run(); }
}
