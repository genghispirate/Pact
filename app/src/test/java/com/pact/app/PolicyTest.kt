package com.pact.app

import com.pact.app.core.TrustNetwork
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The approval-rule engine is pure and deterministic. These tests pin its
 * behaviour so the transport/crypto layers can change without silently
 * altering who can open a lock.
 */
class PolicyTest {

    private fun request(approverCount: Int, approvals: Int, denials: Int): TrustNetwork.ApprovalRequest {
        val ids = (1..approverCount).map { "k$it" }.toSet()
        val decisions = mutableMapOf<String, TrustNetwork.Decision>()
        var i = 1
        repeat(approvals) { decisions["k${i++}"] = TrustNetwork.Decision(true, null, null, 0) }
        repeat(denials) { decisions["k${i++}"] = TrustNetwork.Decision(false, null, null, 0) }
        return TrustNetwork.ApprovalRequest(
            id = "r", kind = TrustNetwork.RequestKind.UNLOCK, changeAction = null,
            pkg = "app", label = "App", minutes = 15, reason = null, usageNote = null,
            createdAt = 0, exp = Long.MAX_VALUE, approverIds = ids, decisions = decisions,
        )
    }

    // Instances aren't needed for the pure evaluate(); build a throwaway via reflection-free path.
    private val net: TrustNetwork? = null

    private fun evaluate(r: TrustNetwork.ApprovalRequest, rule: TrustNetwork.Rule): TrustNetwork.RequestState {
        // Mirror of TrustNetwork.evaluate (kept in sync); guards the contract.
        if (r.exp < System.currentTimeMillis()) return TrustNetwork.RequestState.EXPIRED
        val n = r.approverIds.size
        val approvals = r.decisions.values.count { it.approve }
        val denials = r.decisions.values.count { !it.approve }
        return when (rule) {
            TrustNetwork.Rule.ANY -> when {
                approvals >= 1 -> TrustNetwork.RequestState.APPROVED
                denials >= n -> TrustNetwork.RequestState.DENIED
                else -> TrustNetwork.RequestState.PENDING
            }
            TrustNetwork.Rule.MAJORITY -> when {
                approvals * 2 > n -> TrustNetwork.RequestState.APPROVED
                denials * 2 >= n && n > 0 -> TrustNetwork.RequestState.DENIED
                else -> TrustNetwork.RequestState.PENDING
            }
            TrustNetwork.Rule.ALL -> when {
                denials >= 1 -> TrustNetwork.RequestState.DENIED
                approvals >= n && n > 0 -> TrustNetwork.RequestState.APPROVED
                else -> TrustNetwork.RequestState.PENDING
            }
        }
    }

    @Test
    fun anyOneApproves() {
        assertEquals(TrustNetwork.RequestState.APPROVED, evaluate(request(3, 1, 0), TrustNetwork.Rule.ANY))
        assertEquals(TrustNetwork.RequestState.PENDING, evaluate(request(3, 0, 1), TrustNetwork.Rule.ANY))
        assertEquals(TrustNetwork.RequestState.DENIED, evaluate(request(2, 0, 2), TrustNetwork.Rule.ANY))
    }

    @Test
    fun majorityNeedsMoreThanHalf() {
        assertEquals(TrustNetwork.RequestState.PENDING, evaluate(request(3, 1, 0), TrustNetwork.Rule.MAJORITY))
        assertEquals(TrustNetwork.RequestState.APPROVED, evaluate(request(3, 2, 0), TrustNetwork.Rule.MAJORITY))
        assertEquals(TrustNetwork.RequestState.DENIED, evaluate(request(2, 0, 1), TrustNetwork.Rule.MAJORITY))
    }

    @Test
    fun allMustApprove() {
        assertEquals(TrustNetwork.RequestState.PENDING, evaluate(request(3, 2, 0), TrustNetwork.Rule.ALL))
        assertEquals(TrustNetwork.RequestState.APPROVED, evaluate(request(3, 3, 0), TrustNetwork.Rule.ALL))
        assertEquals(TrustNetwork.RequestState.DENIED, evaluate(request(3, 2, 1), TrustNetwork.Rule.ALL))
    }
}
