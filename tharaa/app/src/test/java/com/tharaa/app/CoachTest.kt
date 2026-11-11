package com.tharaa.app

import com.tharaa.app.data.Income
import com.tharaa.app.data.Setting
import com.tharaa.app.data.T
import com.tharaa.app.data.Txn
import com.tharaa.app.domain.Coach
import com.tharaa.app.domain.Commitment
import com.tharaa.app.domain.CommitmentCodec
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.Investors
import com.tharaa.app.domain.K
import com.tharaa.app.domain.Phase
import com.tharaa.app.domain.RawData
import com.tharaa.app.domain.analyzeCloses
import com.tharaa.app.domain.commitmentCategory
import com.tharaa.app.domain.derive
import com.tharaa.app.domain.stockVerdict
import com.tharaa.app.domain.suggestPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {
    @Test fun futureValueCompounds() {
        // 500 شهرياً بعائد 8% لمدة 10 سنين ≈ 91,473
        assertEquals(91_473.0, Coach.futureValue(0.0, 500.0, 10), 50.0)
        assertEquals(1000.0, Coach.futureValue(1000.0, 0.0, 0), 0.001)
    }

    @Test fun commitmentsCoveredFirstAndTracked() {
        val now = System.currentTimeMillis()
        val c = Commitment(id = 7, name = "جمعية", amount = 300.0)
        val settings = listOf(Setting(K.COMMITMENTS, CommitmentCodec.encode(listOf(c))))
        val raw = RawData(txns = listOf(Txn(type = T.EXPENSE, fund = Fund.ESSENTIALS.key, amount = -300.0, category = commitmentCategory(7), timestamp = now)))
        val s = derive(raw, settings, now)
        assertEquals(300.0, s.commitmentsTotal, 0.001)
        assertTrue(s.commitments.single().isPaid)
        assertEquals(300.0, s.input!!.essentialsBudget, 0.001)
        assertEquals("التزام شهري", com.tharaa.app.domain.Categories.of(commitmentCategory(7)).title)
    }

    @Test fun codecRoundTripAndBadInput() {
        val list = listOf(Commitment(1, "قسط", 250.0, day = 5, kind = "INSTALLMENT", endMonth = "2027-06"))
        assertEquals(list, CommitmentCodec.decode(CommitmentCodec.encode(list)))
        assertTrue(CommitmentCodec.decode("not json").isEmpty())
    }

    @Test fun planFollowsPhase() {
        val early = suggestPlan(1000.0, Phase.FOUNDATION, halal = false, portfolioValue = 0.0)
        assertEquals(listOf("VOO"), early.lines.map { it.symbol })
        val wealth = suggestPlan(1000.0, Phase.WEALTH, halal = true, portfolioValue = 0.0)
        assertEquals(1000.0, wealth.lines.sumOf { it.amount }, 0.01)
        assertTrue(wealth.lines.all { it.symbol in Investors.halalFunds })
    }

    @Test fun stockAnalysis() {
        val up = List(250) { 100.0 + it }
        val st = analyzeCloses(up)
        assertNotNull(st)
        assertTrue(st!!.change1y > 100)
        assertTrue(st.price > st.sma200)
        val v = stockVerdict("VOO", st, halalOnly = false, investCash = 1000.0, portfolioValue = 0.0)
        assertTrue(v.any { it.positive })
        assertTrue(Investors.portfolios.filter { it.holdings.isNotEmpty() }.all { p -> p.holdings.sumOf { it.weight } == 100 })
    }

    @Test fun reportNeedsIncome() {
        val s = derive(RawData(), emptyList())
        assertEquals(1, Coach.report(s).size)
        val now = System.currentTimeMillis()
        val raw = RawData(
            incomes = listOf(Income(id = 1, amount = 3000.0, timestamp = now)),
            txns = listOf(Txn(type = T.ALLOC, fund = Fund.INVESTMENT.key, amount = 900.0, timestamp = now, incomeId = 1)),
        )
        val r = Coach.report(derive(raw, emptyList(), now), now)
        assertTrue(r.size >= 3)
        assertNotNull(Coach.metrics(derive(raw, emptyList(), now), now))
    }

    @Test fun rolloverKeepsUnpaidCommitments() {
        val now = System.currentTimeMillis()
        val last = com.tharaa.app.domain.addMonths(now, -1)
        val c = Commitment(id = 9, name = "جمعية", amount = 300.0)
        val settings = listOf(
            Setting(K.COMMITMENTS, CommitmentCodec.encode(listOf(c))),
            Setting(K.LAST_ROLLOVER, com.tharaa.app.domain.monthKey(last)),
        )
        val raw = RawData(txns = listOf(Txn(type = T.ALLOC, fund = Fund.ESSENTIALS.key, amount = 500.0, timestamp = last)))
        val s = derive(raw, settings, now)
        val move = s.pendingRollover.single { it.from == Fund.ESSENTIALS }
        assertEquals(200.0, move.amount, 0.001) // 300 محجوزة للجمعية اللي ما انسدّت
    }
}
