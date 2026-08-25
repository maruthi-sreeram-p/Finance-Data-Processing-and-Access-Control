package com.fintrack.security;

import com.fintrack.entity.Role;
import org.springframework.stereotype.Component;

/**
 * Who may see and change whose financial records.
 *
 * <p>The rules live in one small class rather than being spread across service
 * methods so that the whole policy can be read at a glance and tested directly. A
 * reviewer asking "can a manager edit someone else's expense?" gets an answer here
 * instead of having to audit every call site.
 *
 * <p>The policy is deliberately asymmetric between reading and writing:
 *
 * <ul>
 *   <li><b>Reading</b> widens with privilege. A MANAGER needs cross-user visibility
 *       to produce reports, and an ADMIN needs it to support users.</li>
 *   <li><b>Writing</b> does not widen at all. Nobody may create, alter or delete
 *       another person's financial records -- not even an administrator. A ledger
 *       whose entries can be silently rewritten by a third party is not trustworthy,
 *       and no support task requires it: the legitimate remedies are for the owner
 *       to correct the entry, or for an administrator to suspend the account.</li>
 * </ul>
 *
 * <p>This is a policy object, not an enforcement point. It answers questions; the
 * service layer is what refuses to act on the answer, because only the service can
 * see which record is being touched.
 */
@Component
public class LedgerAccessPolicy {

    /**
     * Whether the role may read transactions belonging to other users.
     *
     * @return true for ADMIN and MANAGER, false for USER
     */
    public boolean canReadAnyLedger(Role role) {
        return role == Role.ADMIN || role == Role.MANAGER;
    }

    /**
     * Whether the caller may modify the given transaction.
     *
     * <p>Ownership and nothing else. Passing the role in would invite a future
     * "administrators are exempt" clause, which is exactly the property this
     * policy exists to prevent.
     *
     * @param callerId  the authenticated user id
     * @param ownerId   the user id the record belongs to
     */
    public boolean canModify(Long callerId, Long ownerId) {
        return callerId != null && callerId.equals(ownerId);
    }

    /**
     * Whether the caller may read the given transaction.
     *
     * <p>Owners always may; everyone else needs cross-user read privilege.
     */
    public boolean canRead(Long callerId, Role callerRole, Long ownerId) {
        return canModify(callerId, ownerId) || canReadAnyLedger(callerRole);
    }

    /**
     * Resolves which user's ledger a listing request should actually return.
     *
     * <p>A USER is pinned to their own id regardless of what they asked for, so
     * {@code ?userId=42} cannot be used to page through someone else's history.
     * A privileged caller may target another user, and defaults to their own
     * ledger when no target is given.
     *
     * @param requestedUserId the {@code userId} query parameter, or null
     * @return the user id whose transactions may be returned
     */
    public Long resolveLedgerOwner(Long callerId, Role callerRole, Long requestedUserId) {
        if (!canReadAnyLedger(callerRole)) {
            return callerId;
        }
        return requestedUserId == null ? callerId : requestedUserId;
    }
}
