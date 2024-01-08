/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.samples

import android.content.Context
import com.sudoplatform.sudovirtualcards.simulator.BaseTests
import com.sudoplatform.sudovirtualcards.simulator.SudoVirtualCardsSimulatorClient
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * These are sample snippets of code that are included in the generated documentation. They are
 * placed here in the test code so that at least we know they will compile.
 */
@Suppress("UNUSED_VARIABLE")
class Samples : BaseTests() {

    private val context by before { mock<Context>() }

    @Test
    fun mockTest() {
        // Just to keep junit happy
    }

    fun sudoVirtualCardsSimulatorClient(username: String, password: String) {
        val vcSimulatorClient = SudoVirtualCardsSimulatorClient.builder()
            .setContext(context)
            .setUsername(username)
            .setPassword(password)
            .build()
    }

    suspend fun currencyAmount(vcSimulatorClient: SudoVirtualCardsSimulatorClient) {
        // Find the rate of USD to AUD
        val rates = vcSimulatorClient.getSimulatorConversionRates()
        val usd = rates.find { it.currency == "USD" }
        val aud = rates.find { it.currency == "AUD" }
        val usdToAud = (usd?.amount?.toDouble() ?: 0.0) / (aud?.amount?.toDouble() ?: 1.0)
        println("USD to AUD $usdToAud")
    }
}
