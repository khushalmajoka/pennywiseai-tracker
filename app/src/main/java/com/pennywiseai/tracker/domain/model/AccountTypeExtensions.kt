package com.pennywiseai.tracker.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity

/**
 * Converts AccountType enum to database string representation
 */
fun AccountType.toDatabaseString(): String = this.name

/**
 * Converts database string to AccountType enum, with fallback
 */
fun String?.toAccountType(): AccountType = when (this?.uppercase()) {
    "SAVINGS" -> AccountType.SAVINGS
    "CURRENT" -> AccountType.CURRENT
    "CREDIT" -> AccountType.CREDIT
    "CASH" -> AccountType.CASH
    "LOAN" -> AccountType.LOAN
    else -> AccountType.SAVINGS  // Default fallback
}

/**
 * Gets AccountType from AccountBalanceEntity, using accountType field
 * or falling back to isCreditCard flag for backward compatibility
 */
fun AccountBalanceEntity.getAccountType(): AccountType {
    return when {
        accountType != null -> accountType.toAccountType()
        isCreditCard -> AccountType.CREDIT
        else -> AccountType.SAVINGS
    }
}

/**
 * Formats account type for UI display
 */
@Composable
@ReadOnlyComposable
fun AccountType.displayName(): String = stringResource(
    when (this) {
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.CURRENT -> R.string.account_type_current
        AccountType.CREDIT -> R.string.account_type_credit_card
        AccountType.CASH -> R.string.account_type_cash
        AccountType.LOAN -> R.string.account_type_loan
    }
)

/**
 * True for account types whose balance represents money owed rather than
 * money held — Credit and Loan share the same "a credit reduces the balance,
 * a debit increases it" math and the same "not spendable, not income" totals
 * treatment (#792). [AccountBalanceEntity.isCreditCard] alone can't answer
 * this since it's strictly `accountType == CREDIT` — kept that way so
 * Credit-Card-specific UI (credit limit, statement day) doesn't leak onto
 * Loan accounts.
 */
fun AccountType.isLiability(): Boolean = this == AccountType.CREDIT || this == AccountType.LOAN

/**
 * Shared icon for an account type — a Cash/Credit/Loan-specific icon, plain
 * bank icon otherwise. Several account pickers duplicated this exact mapping
 * independently (#792 review); new ones should call this instead.
 */
fun AccountType.icon(): ImageVector = when (this) {
    AccountType.CASH -> Icons.Default.Money
    AccountType.CREDIT -> Icons.Default.CreditCard
    AccountType.LOAN -> Icons.Default.RequestQuote
    AccountType.SAVINGS, AccountType.CURRENT -> Icons.Default.AccountBalance
}

fun AccountBalanceEntity.isLiability(): Boolean = getAccountType().isLiability()

/**
 * Resolves the `isCreditCard` flag to store on a new balance row from SMS
 * ingestion: [smsSaysCredit] (this SMS's own parser signal) OR'd with the
 * existing account's stored flag — UNLESS the account has been manually set
 * to LOAN, which always wins so a later credit-worded SMS can't silently
 * re-flip a reclassified Loan account back into the Credit Cards bucket
 * (#792 review). Shared by [SmsTransactionProcessor] and
 * [OptimizedSmsReaderWorker], the two independent SMS-ingestion paths.
 */
fun resolveIsCreditCardForMath(smsSaysCredit: Boolean, existing: AccountBalanceEntity?): Boolean =
    existing?.getAccountType() != AccountType.LOAN && (smsSaysCredit || existing?.isCreditCard == true)
