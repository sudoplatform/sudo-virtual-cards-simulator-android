/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.regions.Regions
import com.sudoplatform.sudovirtualcards.simulator.auth.SimulatorCognitoUserPoolAuthProvider
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the correct operation of the [SudoVirtualCardsSimulatorClient.builder]
 */
@RunWith(AndroidJUnit4::class)
class SudoVirtualCardsSimulatorClientBuilderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun simulatorClientBuilderShouldThrowIfRequirementsNotProvided() {
        shouldThrow<NullPointerException> {
            SudoVirtualCardsSimulatorClient.builder().build()
        }

        shouldThrow<NullPointerException> {
            SudoVirtualCardsSimulatorClient.builder()
                .setContext(context)
                .build()
        }

        shouldThrow<NullPointerException> {
            SudoVirtualCardsSimulatorClient.builder()
                .setContext(context)
                .build()
        }
    }

    @Test
    fun simulatorClientBuilderShouldNotThrowIfApiKeyProvided() {
        SudoVirtualCardsSimulatorClient.builder()
            .setContext(context)
            .setApiKey("foo")
            .build()
    }

    @Test
    fun simulatorClientBuilderShouldNotThrowIfUsernamePasswordProvided() {
        SudoVirtualCardsSimulatorClient.builder()
            .setContext(context)
            .setUsername("foo")
            .setPassword("bar")
            .build()
    }

    @Test
    fun simulatorClientBuilderShouldNotThrowIfAuthProviderUsed() {
        val authProvider = SimulatorCognitoUserPoolAuthProvider(
            context = context!!,
            poolId = "42",
            clientId = "42",
            region = "42",
            username = "foo",
            password = "bar",
        )
        val appSyncClient = AWSAppSyncClient.builder()
            .context(context)
            .serverUrl("https://smh.com.au")
            .cognitoUserPoolsAuthProvider(authProvider)
            .mutationQueueExecutionTimeout(30)
            .subscriptionsAutoReconnect(true)
            .region(Regions.fromName("us-east-1"))
            .build()
        SudoVirtualCardsSimulatorClient.builder()
            .setAppSyncClient(appSyncClient)
            .build()
    }
}
