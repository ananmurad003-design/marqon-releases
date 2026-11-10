package com.tharaa.app

import com.tharaa.app.data.Debt
import com.tharaa.app.data.Goal
import com.tharaa.app.data.Income
import com.tharaa.app.data.Setting
import com.tharaa.app.data.T
import com.tharaa.app.data.Txn
import com.tharaa.app.domain.Advisor
import com.tharaa.app.domain.AdvisorInput
import com.tharaa.app.domain.Fund
import com.tharaa.app.domain.K
import com.tharaa.app.domain.Phase
import com.tharaa.app.domain.RawData
import com.tharaa.app.domain.addMonths
import com.tharaa.app.domain.derive
import com.tharaa.app.domain.parseAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorTest {
    private fun input(
        balances: Map<Fund, Double> = emptyMap(),
        debt: Double = 0.0,
        base: Double = 1500.0,
        essentials: Double = 0.0,
        essFunded: Double = 0.0,
        avgIncome: Double = 0.0,
        monthIncome: Double = 0.0,
        goalsRoom: Double = 50_000.0,
        custom: Map<Fund, Int>? = null,
    ) = AdvisorInput(balances, debt, base, 4, essentials, essFunded, avgIncome, monthIncome, goalsRoom, "الزواج", null, custom)

    private fun assertSums(amount: Double, lines: Map<Fund, Double>) =
        assertEquals(amount, lines.values.sum(), 0.001)

    @Test fun phases() {
        assertEquals(Phase.FOUNDATION, Advisor.phaseOf(input(debt = 1000.0)))
        assertEquals(Phase.DEBT_FREE, Advisor.phaseOf(input(debt = 1000.0, balances = mapOf(Fund.EMERGENCY to 1500.0))))
        assertEquals(Phase.SECURITY, Advisor.phaseOf(input(balances = mapOf(Fund.EMERGENCY to 1500.0))))
        assertEquals(Phase.WEALTH, Advisor.phaseOf(input(balances = mapOf(Fund.EMERGENCY to 6000.0))))
    }

    @Test fun foundationSplitWithDebt() {
        val p = Advisor.plan(2000.0, input(debt = 5000.0))
        assertSums(2000.0, p.lines)
        assertEquals(800.0, p.lines[Fund.DEBT]!!, 0.001)       // 40%
        assertEquals(400.0, p.lines[Fund.EMERGENCY]!!, 0.001)  // 20%
        assertEquals(200.0, p.lines[Fund.INVESTMENT]!!, 0.001) // 10% دائماً
        assertEquals(400.0, p.lines[Fund.PERSONAL]!!, 0.001)
    }

    @Test fun debtCapOverflowsToInvestment() {
        val p = Advisor.plan(5000.0, input(debt = 300.0))
        assertSums(5000.0, p.lines)
        assertEquals(300.0, p.lines[Fund.DEBT]!!, 0.001)
        assertTrue(p.lines[Fund.INVESTMENT]!! > 500.0)
    }

    @Test fun emergencyFullGoesToWealth() {
        val p = Advisor.plan(3000.0, input(balances = mapOf(Fund.EMERGENCY to 6000.0)))
        assertEquals(Phase.WEALTH, p.phase)
        assertEquals(0.0, p.lines[Fund.EMERGENCY]!!, 0.001)
        assertEquals(1200.0, p.lines[Fund.INVESTMENT]!!, 0.001)
        assertEquals(900.0, p.lines[Fund.GOALS]!!, 0.001)
        assertSums(3000.0, p.lines)
    }

    @Test fun essentialsFirst() {
        val p = Advisor.plan(2000.0, input(essentials = 1000.0))
        assertEquals(1000.0, p.lines[Fund.ESSENTIALS]!!, 0.001)
        assertSums(2000.0, p.lines)
        val p2 = Advisor.plan(1000.0, input(essentials = 1000.0))
        assertEquals(600.0, p2.lines[Fund.ESSENTIALS]!!, 0.001) // بحد أقصى 60%
    }

    @Test fun windfallMovesPersonalToInvestment() {
        val normal = Advisor.plan(4000.0, input(balances = mapOf(Fund.EMERGENCY to 6000.0)))
        val windfall = Advisor.plan(4000.0, input(balances = mapOf(Fund.EMERGENCY to 6000.0), avgIncome = 3000.0, monthIncome = 2000.0))
        assertTrue(windfall.lines[Fund.PERSONAL]!! < normal.lines[Fund.PERSONAL]!!)
        assertTrue(windfall.lines[Fund.INVESTMENT]!! > normal.lines[Fund.INVESTMENT]!!)
        assertSums(4000.0, windfall.lines)
    }

    @Test fun noGoalsRoomRedirectsToInvestment() {
        val p = Advisor.plan(1000.0, input(balances = mapOf(Fund.EMERGENCY to 6000.0), goalsRoom = 0.0))
        assertEquals(0.0, p.lines[Fund.GOALS]!!, 0.001)
        assertEquals(700.0, p.lines[Fund.INVESTMENT]!!, 0.001)
    }

    @Test fun reviewWarnsOnLowInvestment() {
        val i = input(debt = 1000.0)
        val rec = Advisor.plan(1000.0, i).lines
        val edited = rec.toMutableMap().apply { this[Fund.INVESTMENT] = 0.0; this[Fund.PERSONAL] = this[Fund.PERSONAL]!! + 100 }
        val w = Advisor.review(edited, rec, 1000.0, i)
        assertTrue(w.none { it.severe })
        assertTrue(w.any { it.text.contains("10%") })
        val bad = rec.toMutableMap().apply { this[Fund.PERSONAL] = 0.0 }
        assertTrue(Advisor.review(bad, rec, 1000.0, i).any { it.severe })
    }

    @Test fun parsesArabicDigits() {
        assertEquals(200.0, parseAmount("٢٠٠")!!, 0.0)
        assertEquals(1500.5, parseAmount("1,500.5")!!, 0.0)
        assertNull(parseAmount("abc"))
    }

    @Test fun deriveBalancesAndRollover() {
        val now = System.currentTimeMillis()
        val last = addMonths(now, -1)
        val raw = RawData(
            txns = listOf(
                Txn(type = T.ALLOC, fund = Fund.PERSONAL.key, amount = 400.0, timestamp = last, incomeId = 1),
                Txn(type = T.EXPENSE, fund = Fund.PERSONAL.key, amount = -100.0, timestamp = last, category = "fun"),
                Txn(type = T.ALLOC, fund = Fund.DEBT.key, amount = 300.0, timestamp = last, incomeId = 1),
                Txn(type = T.DEBT_PAY, fund = Fund.DEBT.key, amount = -300.0, timestamp = last, debtId = 1),
            ),
            incomes = listOf(Income(id = 1, amount = 700.0, timestamp = last)),
            debts = listOf(Debt(id = 1, name = "صاحبي", total = 1000.0, createdAt = last)),
            goals = listOf(Goal(id = 1, name = "الزواج", target = 50000.0, priority = 1, createdAt = last)),
        )
        val s = derive(raw, listOf(Setting(K.LAST_ROLLOVER, com.tharaa.app.domain.monthKey(last))), now)
        assertEquals(300.0, s.bal(Fund.PERSONAL), 0.001)
        assertEquals(700.0, s.debtRemaining, 0.001)
        assertEquals(Phase.FOUNDATION, s.phase)
        assertEquals(1, s.pendingRollover.size)
        assertEquals(Fund.EMERGENCY, s.pendingRollover[0].to)
        assertEquals(300.0, s.pendingRollover[0].amount, 0.001)
    }
}
