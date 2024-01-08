/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateAuthorizationInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateDebitInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateRefundInput
import com.sudoplatform.sudovirtualcards.types.JsonValue
import com.sudoplatform.sudovirtualcards.types.inputs.CreditCardFundingSourceInput
import com.sudoplatform.sudovirtualcards.types.inputs.ProvisionVirtualCardInput
import io.kotlintest.fail
import io.kotlintest.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * Test the speed of transaction handling
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SudoVirtualCardsSimulatorTransactionPerformanceTest : BaseTest() {

    companion object {
        private const val NUMBER_TRANSACTIONS = 100
        private const val MAX_MEAN_MS_PER_TXN: Double = 2500.0
    }

    enum class Step {
        CREATE_FUNDING_SOURCE,
        CREATE_SUDO,
        CREATE_CARD,
        GET_MERCHANTS,
        AUTHORIZE,
        CREATE_DEBITS,
        CREATE_REFUNDS,
        LIST_TRANSACTIONS,
    }

    private val timings = mutableMapOf<Step, Long>()

    @Before
    fun setup() {
        super.init()
    }

    @After
    fun tearDown() {
        super.fini()
        timings.forEach { println("timing: ${it.key} took ${it.value} ms") }
        timings.clear()
    }

    private suspend fun <T> measure(step: Step, block: suspend () -> T): T {
        val start = Instant.now()
        val result = block.invoke()
        val end = Instant.now()

        timings[step] = Duration.between(start, end).toMillis()
        return result
    }

    @Test
    fun measureTimeToListManyTransactions() = runBlocking<Unit> {
        assumeTrue(apiKeyPresent())
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
            AndroidTestData.VerifiedUser.country,
        )
        val fundingSource = measure(Step.CREATE_FUNDING_SOURCE) {
            createFundingSource(vcClient, fundingSourceInput)
        }

        // Create a Sudo
        val sudo = measure(Step.CREATE_SUDO) {
            createSudo(AndroidTestData.sudo)
        }

        // Create a virtual card
        val ownershipProof = getOwnershipProof(sudo)
        vcClient.createKeysIfAbsent()
        val cardInput = ProvisionVirtualCardInput(
            ownershipProofs = listOf(ownershipProof),
            fundingSourceId = fundingSource.id,
            cardHolder = AndroidTestData.VirtualUser.cardHolder,
            metadata = JsonValue.JsonString(AndroidTestData.VirtualUser.alias),
            addressLine1 = AndroidTestData.VirtualUser.addressLine1,
            city = AndroidTestData.VirtualUser.city,
            state = AndroidTestData.VirtualUser.state,
            postalCode = AndroidTestData.VirtualUser.postalCode,
            country = AndroidTestData.VirtualUser.country,
            currency = "USD",
        )
        val card = measure(Step.CREATE_CARD) {
            createCard(cardInput)
        }
        logger.debug("$card")

        // Create an authorization for a purchase (debit)
        val merchant = measure(Step.GET_MERCHANTS) {
            simulatorClient.getSimulatorMerchants()
        }.first()

        val originalAmount = 10 * NUMBER_TRANSACTIONS

        val authInput = SimulateAuthorizationInput(
            cardNumber = card.cardNumber,
            amount = originalAmount,
            merchantId = merchant.id,
            securityCode = card.securityCode,
            expirationMonth = card.expiry.mm.toInt(),
            expirationYear = card.expiry.yyyy.toInt(),
        )

        val authResponse = measure(Step.AUTHORIZE) {
            simulatorClient.simulateAuthorization(authInput)
        }
        authResponse.isApproved shouldBe true

        // Create lots of debits
        val debitInput = SimulateDebitInput(
            authorizationId = authResponse.id,
            amount = authInput.amount / NUMBER_TRANSACTIONS,
        )
        val debitIds = mutableListOf<String>()
        measure(Step.CREATE_DEBITS) {
            for (i in 1..NUMBER_TRANSACTIONS) {
                debitIds.add(simulatorClient.simulateDebit(debitInput).id)
            }
        }

        // Refund the debits
        measure(Step.CREATE_REFUNDS) {
            debitIds.forEach { id ->
                val refundInput = SimulateRefundInput(
                    debitId = id,
                    amount = authInput.amount / NUMBER_TRANSACTIONS,
                )
                simulatorClient.simulateRefund(refundInput)
            }
        }

        // Pause to allow some processing to catch up
        delay(10_000)

        // List the debit transactions and measure the time it takes
        measure(Step.LIST_TRANSACTIONS) {
            val transactions = listTransactions(card)
            val txnTypes = mutableMapOf<String, Long>()
            transactions.forEach { txn ->
                txnTypes[txn.type.name] = (txnTypes[txn.type.name] ?: 0) + 1
            }
            println("timing: transaction count ${transactions.size} $txnTypes")
        }

        // Check the timings
        val failures = buildString {
            timings.forEach { step, totalMilliseconds ->
                val meanMillisecondsPerTxn = totalMilliseconds.toDouble() / NUMBER_TRANSACTIONS.toDouble()
                if (meanMillisecondsPerTxn > MAX_MEAN_MS_PER_TXN) {
                    appendLine("$step had mean of $meanMillisecondsPerTxn ms per transaction (should be < $MAX_MEAN_MS_PER_TXN)")
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures)
        }
    }
}
