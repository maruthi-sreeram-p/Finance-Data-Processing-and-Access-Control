package com.fintrack.security;

import com.fintrack.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The access policy is small enough to test exhaustively, and important enough to
 * be worth doing so: every rule below is one an attacker would try to break.
 */
class LedgerAccessPolicyTest {

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;

    private final LedgerAccessPolicy policy = new LedgerAccessPolicy();

    @Test
    @DisplayName("privileged roles may read across users, a plain user may not")
    void crossUserReadFollowsRole() {
        assertThat(policy.canReadAnyLedger(Role.ADMIN)).isTrue();
        assertThat(policy.canReadAnyLedger(Role.MANAGER)).isTrue();
        assertThat(policy.canReadAnyLedger(Role.USER)).isFalse();
    }

    @Test
    @DisplayName("an owner may modify their own record")
    void ownerMayModify() {
        assertThat(policy.canModify(ALICE, ALICE)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("no role, administrator included, may modify another user's record")
    void nobodyMayModifyAnotherUsersRecord(Role role) {
        // The rule the whole policy exists for: write access never widens with
        // privilege. A ledger a third party can silently rewrite is not trustworthy.
        assertThat(policy.canModify(ALICE, BOB)).isFalse();
        assertThat(policy.canRead(ALICE, role, BOB)).isEqualTo(policy.canReadAnyLedger(role));
    }

    @Test
    @DisplayName("treats a null caller as unauthorized rather than matching a null owner")
    void nullCallerNeverMatches() {
        assertThat(policy.canModify(null, null)).isFalse();
        assertThat(policy.canModify(null, ALICE)).isFalse();
    }

    @Test
    @DisplayName("pins a plain user to their own ledger whatever they request")
    void pinsPlainUserToOwnLedger() {
        // The horizontal-escalation attempt: ?userId=2 as a plain user.
        assertThat(policy.resolveLedgerOwner(ALICE, Role.USER, BOB)).isEqualTo(ALICE);
        assertThat(policy.resolveLedgerOwner(ALICE, Role.USER, null)).isEqualTo(ALICE);
    }

    @Test
    @DisplayName("lets a privileged caller target another ledger, defaulting to their own")
    void allowsPrivilegedCallerToTargetAnotherLedger() {
        assertThat(policy.resolveLedgerOwner(ALICE, Role.ADMIN, BOB)).isEqualTo(BOB);
        assertThat(policy.resolveLedgerOwner(ALICE, Role.MANAGER, BOB)).isEqualTo(BOB);
        assertThat(policy.resolveLedgerOwner(ALICE, Role.ADMIN, null)).isEqualTo(ALICE);
    }
}
