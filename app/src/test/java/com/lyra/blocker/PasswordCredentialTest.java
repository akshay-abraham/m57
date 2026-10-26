// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

import org.junit.Test;

public class PasswordCredentialTest {
    @Test public void passwordValidationHashingAndLegacyVerification() throws Exception {
        PasswordCredentialChecks.run();
    }
}
