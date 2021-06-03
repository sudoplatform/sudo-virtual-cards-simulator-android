/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateAuthorizationInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateDebitInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateIncrementalAuthorizationInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateRefundInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateReversalInput
import com.sudoplatform.sudovirtualcards.subscribeToTransactions
import com.sudoplatform.sudovirtualcards.types.Transaction
import com.sudoplatform.sudovirtualcards.types.inputs.CreditCardFundingSourceInput
import com.sudoplatform.sudovirtualcards.types.inputs.ProvisionCardInput
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldNotBeLessThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.UUID

/**
 * Test the correct operation of the [SudoVirtualCardsSimulatorClient]
 *
 * @since 2020-05-26
 */
@RunWith(AndroidJUnit4::class)
class SudoVirtualCardsSimulatorClientIntegrationTest : BaseTest() {

    @Before
    fun setup() {
        super.init()
    }

    @After
    fun tearDown() {
        super.fini()
    }

    @Test
    fun getSimulatorMerchantsShouldReturnResults() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())

        val merchants = simulatorClient.getSimulatorMerchants()
        merchants.isEmpty() shouldBe false

        merchants.forEach { merchant ->
            with(merchant) {
                id.isBlank() shouldBe false
                name.isBlank() shouldBe false
                description.isBlank() shouldBe false
                mcc.isBlank() shouldBe false
                city.isBlank() shouldBe false
                country.isBlank() shouldBe false
                currency.isBlank() shouldBe false
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
            }
        }
    }

    @Test
    fun getSimulatorConversionRatesShouldReturnResults() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())

        val currencies = simulatorClient.getSimulatorConversionRates()
        currencies.isEmpty() shouldBe false

        currencies.forEach { currency ->
            with(currency) {
                this.currency.isBlank() shouldBe false
                amount shouldNotBe 0
            }
        }

        currencies.first { it.currency == "USD" }
        currencies.first { it.currency == "AUD" }
        currencies.first { it.currency == "EUR" }
    }

    @Test
    fun completeFlowShouldSucceed() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())
        assumeTrue(clientConfigFilesPresent())

        // Log in and perform ID verification
        signInAndRegister()
        verifyTestUserIdentity()
        refreshTokens()

        // Add a subscriber for transactions
        val transactionUpdates = mutableListOf<Transaction>()
        val subscriptionId = UUID.randomUUID().toString()
        vcardsClient.subscribeToTransactions(subscriptionId) { txn ->
            transactionUpdates.add(txn)
        }

        // Create a funding source
        val fundingSourceInput = CreditCardFundingSourceInput(
            AndroidTestData.Visa.cardNumber,
            expirationMonth,
            expirationYear,
            AndroidTestData.Visa.securityCode,
            AndroidTestData.VerifiedUser.addressLine1,
            AndroidTestData.VerifiedUser.addressLine2,
            AndroidTestData.VerifiedUser.city,
            AndroidTestData.VerifiedUser.state,
            AndroidTestData.VerifiedUser.postalCode,
            AndroidTestData.VerifiedUser.country
        )
        val fundingSource = vcardsClient.createFundingSource(fundingSourceInput)

        // Create a Sudo
        val sudo = createSudo(AndroidTestData.sudo)
        sudosToDelete.add(sudo)

        // Create a virtual card
        val cardInput = ProvisionCardInput(
            sudoId = sudo.id!!,
            fundingSourceId = fundingSource.id,
            cardHolder = AndroidTestData.VirtualUser.cardHolder,
            alias = AndroidTestData.VirtualUser.alias,
            addressLine1 = AndroidTestData.VirtualUser.addressLine1,
            city = AndroidTestData.VirtualUser.city,
            state = AndroidTestData.VirtualUser.state,
            postalCode = AndroidTestData.VirtualUser.postalCode,
            country = AndroidTestData.VirtualUser.country,
            currency = "USD"
        )
        val card = createCard(cardInput)
        logger.debug("$card")

        // Create an authorization for a purchase (debit)
        val merchant = simulatorClient.getSimulatorMerchants().first()
        logger.debug("$merchant")

        val originalAmount = 1042

        val authInput = SimulateAuthorizationInput(
            cardNumber = card.cardNumber,
            amount = originalAmount,
            merchantId = merchant.id,
            securityCode = card.securityCode,
            expirationMonth = card.expirationMonth,
            expirationYear = card.expirationYear
        )

        val authResponse = simulatorClient.simulateAuthorization(authInput)
        logger.debug("$authResponse")
        with(authResponse) {
            isApproved shouldBe true
            amount shouldBe authInput.amount
            currency shouldBe merchant.currency
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
            declineReason shouldBe null
        }

        // Increase the value of the authorization
        val incAuthInput = SimulateIncrementalAuthorizationInput(
            authorizationId = authResponse.id,
            amount = authInput.amount
        )
        val incAuthResponse = simulatorClient.simulateIncrementalAuthorization(incAuthInput)
        logger.debug("$incAuthResponse")
        with(incAuthResponse) {
            isApproved shouldBe true
            amount shouldBe incAuthInput.amount
            currency shouldBe merchant.currency
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
            declineReason shouldBe null
        }

        // Create a debit for the originally authorized amount
        val debitInput = SimulateDebitInput(
            authorizationId = authResponse.id,
            amount = authInput.amount
        )
        val debitResponse = simulatorClient.simulateDebit(debitInput)
        logger.debug("$debitResponse")
        with(debitResponse) {
            id.isBlank() shouldBe false
            amount shouldBe authInput.amount
            currency shouldBe merchant.currency
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
        }

        // Refund the debit in two halves
        val refundInput = SimulateRefundInput(
            debitId = debitResponse.id,
            amount = debitInput.amount / 2
        )
        for (i in 0..1) {
            val refundResponse = simulatorClient.simulateRefund(refundInput)
            logger.debug("$refundResponse")
            with(refundResponse) {
                id.isBlank() shouldBe false
                amount shouldBe refundInput.amount
                currency shouldBe merchant.currency
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
            }
        }
        // Try and refund more than the original debit
        shouldThrow<SudoVirtualCardsSimulatorClient.RefundException.ExcessiveRefundException> {
            simulatorClient.simulateRefund(refundInput)
        }

        // Reverse the authorization. Refunds don't affect things so only the incremental auth amount can be reversed
        val reversalInput = SimulateReversalInput(
            authorizationId = authResponse.id,
            amount = incAuthInput.amount
        )
        val reversalResponse = simulatorClient.simulateReversal(reversalInput)
        logger.debug("$reversalResponse")
        with(reversalResponse) {
            id.isBlank() shouldBe false
            amount shouldBe reversalInput.amount
            currency shouldBe merchant.currency
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
        }

        // List the transactions and wait until there are no PENDING transactions
        val transactions = mutableListOf<Transaction>()
        withTimeout(20_000L) {
            while (transactions.isEmpty() || transactions.any { it.type == Transaction.Type.PENDING }) {
                delay(2_000L)
                transactions.clear()
                transactions.addAll(listTransactions(card))
            }
        }
        transactions.size shouldBe 3

        // Check the transaction that corresponds to the debit has the right amounts
        fun checkDebitHasCorrectAmounts(debitTransaction: Transaction) {
            with(debitTransaction) {
                billedAmount.currency shouldBe merchant.currency
                billedAmount.amount shouldBe originalAmount
                transactedAmount.currency shouldBe merchant.currency
                transactedAmount.amount shouldBe originalAmount
                details.size shouldBe 1
                with(details[0]) {
                    virtualCardAmount.currency shouldBe merchant.currency
                    virtualCardAmount.amount shouldBe originalAmount
                    fundingSourceAmount.currency shouldBe fundingSource.currency
                    fundingSourceAmount.amount shouldBe (virtualCardAmount.amount + markupAmount.amount)
                }
            }
        }
        checkDebitHasCorrectAmounts(transactions.first { it.type == Transaction.Type.COMPLETE })
        checkDebitHasCorrectAmounts(transactionUpdates.first { it.type == Transaction.Type.COMPLETE })

        // Check the transactions that correspond to the refunds have the right amounts
        fun checkRefundsHaveCorrectAmounts(refunds: List<Transaction>) {
            refunds.forEach { refundTransaction ->
                with(refundTransaction) {
                    billedAmount.currency shouldBe merchant.currency
                    billedAmount.amount shouldBe refundInput.amount
                    transactedAmount.currency shouldBe merchant.currency
                    transactedAmount.amount shouldBe refundInput.amount
                    details.size shouldBe 1
                    with(details[0]) {
                        virtualCardAmount.currency shouldBe merchant.currency
                        virtualCardAmount.amount shouldNotBeLessThan refundInput.amount
                        fundingSourceAmount.currency shouldBe fundingSource.currency
                        fundingSourceAmount.amount shouldNotBeLessThan refundInput.amount
                        markupAmount.currency shouldBe fundingSource.currency
                        markupAmount.amount shouldBe 0
                    }
                }
            }
        }
        checkRefundsHaveCorrectAmounts(transactions.filter { it.type == Transaction.Type.REFUND })
        checkRefundsHaveCorrectAmounts(transactionUpdates.filter { it.type == Transaction.Type.REFUND })

        // Check the operation of the getTransaction API
        transactions.forEach { expected ->
            expected shouldBe vcardsClient.getTransaction(expected.id)
        }

        // Check that an update was received for each transaction
        transactions.forEach { expected ->
            val updates = transactionUpdates.filter { it.id == expected.id }
            updates.isEmpty() shouldBe false
        }

        vcardsClient.unsubscribeFromTransactions(subscriptionId)
    }

    @Test
    fun simulateAuthorizationShouldFailWithBogusCard() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())

        val merchant = simulatorClient.getSimulatorMerchants().first()

        val input = SimulateAuthorizationInput(
            cardNumber = "6666666666666666",
            amount = 42_00,
            merchantId = merchant.id,
            expirationMonth = expirationCalendar.get(Calendar.MONTH) + 1,
            expirationYear = expirationCalendar.get(Calendar.YEAR)
        )

        shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.CardNotFoundException> {
            simulatorClient.simulateAuthorization(input)
        }
    }

    @Test
    fun simulateIncrementalAuthorizationShouldFailWithBogusAuthorization() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())

        val input = SimulateIncrementalAuthorizationInput(
            authorizationId = "6666666666666666",
            amount = 42_00
        )

        shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.AuthorizationNotFoundException> {
            simulatorClient.simulateIncrementalAuthorization(input)
        }
    }

    @Test
    fun simulateDebitShouldFailWithBogusAuthorization() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())

        val input = SimulateDebitInput(
            authorizationId = "6666666666666666",
            amount = 42_00
        )

        shouldThrow<SudoVirtualCardsSimulatorClient.DebitException.AuthorizationNotFoundException> {
            simulatorClient.simulateDebit(input)
        }
    }

    @Test
    fun simulateRefundShouldFailWithBogusDebit() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())

        val input = SimulateRefundInput(
            debitId = "6666666666666666",
            amount = 42_00
        )

        shouldThrow<SudoVirtualCardsSimulatorClient.RefundException.DebitNotFoundException> {
            simulatorClient.simulateRefund(input)
        }
    }

    @Test
    fun simulateReversalShouldFailWithBogusAuthorization() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())

        val input = SimulateReversalInput(
            authorizationId = "6666666666666666",
            amount = 42_00
        )

        shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.AuthorizationNotFoundException> {
            simulatorClient.simulateReversal(input)
        }
    }

    @Test
    fun authorizationExpiryExpiredAuthorizationShouldFail() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())
        assumeTrue(clientConfigFilesPresent())

        // Log in and perform ID verification
        signInAndRegister()
        verifyTestUserIdentity()
        refreshTokens()

        // Create a funding source
        val fundingSourceInput = CreditCardFundingSourceInput(
            AndroidTestData.Visa.cardNumber,
            expirationMonth,
            expirationYear,
            AndroidTestData.Visa.securityCode,
            AndroidTestData.VerifiedUser.addressLine1,
            AndroidTestData.VerifiedUser.addressLine2,
            AndroidTestData.VerifiedUser.city,
            AndroidTestData.VerifiedUser.state,
            AndroidTestData.VerifiedUser.postalCode,
            AndroidTestData.VerifiedUser.country
        )
        val fundingSource = vcardsClient.createFundingSource(fundingSourceInput)

        // Create a Sudo
        val sudo = createSudo(AndroidTestData.sudo)
        sudosToDelete.add(sudo)

        // Create a virtual card
        val cardInput = ProvisionCardInput(
            sudoId = sudo.id!!,
            fundingSourceId = fundingSource.id,
            cardHolder = AndroidTestData.VirtualUser.cardHolder,
            alias = AndroidTestData.VirtualUser.alias,
            addressLine1 = AndroidTestData.VirtualUser.addressLine1,
            city = AndroidTestData.VirtualUser.city,
            state = AndroidTestData.VirtualUser.state,
            postalCode = AndroidTestData.VirtualUser.postalCode,
            country = AndroidTestData.VirtualUser.country,
            currency = "USD"
        )
        val card = createCard(cardInput)
        logger.debug("$card")

        // Create an authorization for a purchase (debit)
        val merchant = simulatorClient.getSimulatorMerchants().first()
        logger.debug("$merchant")

        val now = Calendar.getInstance()
        val originalAmount = (now.get(Calendar.HOUR_OF_DAY) * 100) + now.get(Calendar.MINUTE) + (now.get(Calendar.SECOND) / 100)

        val authInput = SimulateAuthorizationInput(
            cardNumber = card.cardNumber,
            amount = originalAmount,
            merchantId = merchant.id,
            securityCode = card.securityCode,
            expirationMonth = card.expirationMonth,
            expirationYear = card.expirationYear
        )

        val authResponse = simulatorClient.simulateAuthorization(authInput)
        logger.debug("$authResponse")
        with(authResponse) {
            id.isBlank() shouldBe false
            isApproved shouldBe true
            amount shouldBe authInput.amount
            currency shouldBe merchant.currency
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
            declineReason shouldBe null
        }

        // Cause the authorization to expire
        val expireResponse = simulatorClient.simulateAuthorizationExpiry(authResponse.id)
        logger.debug("$expireResponse")
        with(expireResponse) {
            id.isBlank() shouldBe false
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
        }

        // Try and expire the authorization again and check it fails
        shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.AuthorizationExpiredException> {
            simulatorClient.simulateAuthorizationExpiry(authResponse.id)
        }
    }

    @Test
    fun simulateAuthorizationExpiryShouldFailWithBogusAuthorization() = runBlocking<Unit> {
        assumeTrue(loginCredentialsPresent())

        shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.AuthorizationNotFoundException> {
            simulatorClient.simulateAuthorizationExpiry("6666666666666666")
        }
    }
}
