package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.utils.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class AccountTypeLoanRulesTest {

    private fun account(accountType: String?, isCreditCard: Boolean = false) = AccountBalanceEntity(
        bankName = "TestBank",
        accountLast4 = "0001",
        balance = BigDecimal("100.00"),
        timestamp = LocalDateTime.now(),
        accountType = accountType,
        isCreditCard = isCreditCard
    )

    @Test
    fun `LOAN and CREDIT are liabilities, the rest are not`() {
        assertTrue(AccountType.LOAN.isLiability())
        assertTrue(AccountType.CREDIT.isLiability())
        assertFalse(AccountType.SAVINGS.isLiability())
        assertFalse(AccountType.CURRENT.isLiability())
        assertFalse(AccountType.CASH.isLiability())
    }

    @Test
    fun `a stored LOAN type parses to LOAN and is a liability without being a credit card`() {
        val loan = account("LOAN")
        assertEquals(AccountType.LOAN, loan.getAccountType())
        assertTrue(loan.isLiability())
        assertFalse(loan.isCreditCard)
    }

    @Test
    fun `sameLiabilityType allows Credit with Credit and Loan with Loan only`() {
        assertTrue(sameLiabilityType(account("CREDIT"), account("CREDIT")))
        assertTrue(sameLiabilityType(account("LOAN"), account("LOAN")))
        assertFalse(sameLiabilityType(account("CREDIT"), account("LOAN")))
        assertFalse(sameLiabilityType(account("LOAN"), account("CREDIT")))
    }

    @Test
    fun `sameLiabilityType never mixes a liability with a spendable account`() {
        assertFalse(sameLiabilityType(account("LOAN"), account("SAVINGS")))
        assertFalse(sameLiabilityType(account("CREDIT"), account("CURRENT")))
        assertFalse(sameLiabilityType(account("CASH"), account("LOAN")))
    }

    @Test
    fun `sameLiabilityType lets spendable accounts of different kinds merge`() {
        assertTrue(sameLiabilityType(account("SAVINGS"), account("CURRENT")))
        assertTrue(sameLiabilityType(account("CASH"), account(null)))
    }

    @Test
    fun `resolveIsCreditCardForMath keeps a manually set LOAN from being flipped by an SMS credit signal`() {
        assertFalse(resolveIsCreditCardForMath(smsSaysCredit = true, existing = account("LOAN")))
        assertFalse(resolveIsCreditCardForMath(smsSaysCredit = false, existing = account("LOAN")))
    }

    @Test
    fun `resolveIsCreditCardForMath still honours the SMS signal and the stored flag for non-loans`() {
        assertTrue(resolveIsCreditCardForMath(smsSaysCredit = true, existing = null))
        assertTrue(resolveIsCreditCardForMath(smsSaysCredit = true, existing = account("SAVINGS")))
        assertTrue(resolveIsCreditCardForMath(smsSaysCredit = false, existing = account("CREDIT", isCreditCard = true)))
        assertFalse(resolveIsCreditCardForMath(smsSaysCredit = false, existing = account("SAVINGS")))
        assertFalse(resolveIsCreditCardForMath(smsSaysCredit = false, existing = null))
    }

    @Test
    fun `an expense raises what is owed on a liability and INCOME lowers it`() {
        val amount = BigDecimal("500")
        // The Credit-Card-vs-Loan distinction in Mark-as-Paid rests on this: a purchase on a
        // card (EXPENSE) must raise outstanding; only a Loan repayment is booked as INCOME.
        assertEquals(amount, BalanceCalculator.signedBalanceEffect(true, TransactionType.EXPENSE, amount))
        assertEquals(amount.negate(), BalanceCalculator.signedBalanceEffect(true, TransactionType.INCOME, amount))
        assertEquals(amount.negate(), BalanceCalculator.signedBalanceEffect(false, TransactionType.EXPENSE, amount))
    }
}
