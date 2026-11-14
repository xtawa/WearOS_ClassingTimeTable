package com.classing.shared.ai

import kotlin.test.*

class PointBalanceTest {
    @Test fun blockedAccountHasNoSpendableTotalDespitePurchasedBalance() {assertEquals(0L,pointBalance(1000,10,0,2000,blocked=true).totalAvailable)}
    @Test fun weeklyAndCreditsStaySeparate() {
        val b = pointBalance(1000, 300, 50, 200)
        assertEquals(650L, b.weeklyRemaining); assertEquals(200L, b.creditsAvailable); assertEquals(850L, b.totalAvailable)
    }
    @Test fun reservationsCoverPurchasedCreditsAfterWeeklyAllowance() {
        val b = pointBalance(1000, 980, 100, 200)
        assertEquals(0L, b.weeklyRemaining); assertEquals(120L, b.creditsAvailable); assertEquals(120L, b.totalAvailable)
    }
    @Test fun frozenAndOverusedCannotProduceNegativeAvailability() {
        assertEquals(0L, pointBalance(10, 20, 50, 0).totalAvailable)
    }
    @Test fun unlimitedAndLargeBalancesDoNotOverflow() {
        assertNull(pointBalance(-1, 1, 2, 3).weeklyRemaining)
        assertEquals(4294967294L, pointBalance(Int.MAX_VALUE, 0, 0, Int.MAX_VALUE).totalAvailable)
    }
}
