/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.auth

import android.content.Context
import com.amazonaws.mobile.client.results.Token
import com.amazonaws.mobile.client.results.Tokens
import com.sudoplatform.sudovirtualcards.simulator.BaseTests
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.lang.IllegalStateException

/**
 * Test the correct operation of the [SimulatorCognitoUserPoolAuthProvider] using mocks and spies.
 */
class SimulatorCognitoUserPoolAuthProviderTest : BaseTests() {
    private val mockContext by before {
        mock<Context>()
    }

    private val mockTokens by before {
        mock<Tokens>().stub {
            on { accessToken } doReturn Token("access")
            on { idToken } doReturn Token("id")
            on { refreshToken } doReturn Token("refresh")
        }
    }

    private val mockUserPoolAuthenticator by before {
        mock<UserPoolAuthenticator>().stub {
            onBlocking { getTokens() } doReturn mockTokens
        }
    }

    private val authProvider by before {
        SimulatorCognitoUserPoolAuthProvider(
            mockContext,
            "poolId",
            "clientId",
            "region",
            "username",
            "password",
            mockUserPoolAuthenticator,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockUserPoolAuthenticator, mockTokens)
    }

    @Test
    fun `should succeed if able to authenticate`() =
        runBlocking<Unit> {
            mockUserPoolAuthenticator.stub {
                on { state } doReturnConsecutively
                    listOf(
                        UserPoolAuthenticator.State.UNKNOWN,
                        UserPoolAuthenticator.State.SIGNED_IN,
                    )
            }

            authProvider.latestAuthToken

            verify(mockUserPoolAuthenticator, times(2)).state
            verify(mockUserPoolAuthenticator).initialize()
            verify(mockUserPoolAuthenticator).signIn("username", "password")
            verify(mockUserPoolAuthenticator).getTokens()
            verify(mockTokens).accessToken
        }

    @Test
    fun `should throw if unable to authenticate`() =
        runBlocking<Unit> {
            mockUserPoolAuthenticator.stub {
                on { state } doReturnConsecutively
                    listOf(
                        UserPoolAuthenticator.State.UNKNOWN,
                        UserPoolAuthenticator.State.SIGNED_OUT,
                    )
            }

            shouldThrow<IllegalStateException> {
                authProvider.latestAuthToken
            }

            verify(mockUserPoolAuthenticator, times(2)).state
            verify(mockUserPoolAuthenticator).initialize()
            verify(mockUserPoolAuthenticator).signIn("username", "password")
        }

    @Test
    fun `should throw if tokens not returned`() =
        runBlocking<Unit> {
            mockUserPoolAuthenticator.stub {
                on { state } doReturnConsecutively
                    listOf(
                        UserPoolAuthenticator.State.UNKNOWN,
                        UserPoolAuthenticator.State.SIGNED_IN,
                    )
                onBlocking { getTokens() } doReturn null
            }

            shouldThrow<IllegalStateException> {
                authProvider.latestAuthToken
            }

            verify(mockUserPoolAuthenticator, times(2)).state
            verify(mockUserPoolAuthenticator).initialize()
            verify(mockUserPoolAuthenticator).signIn("username", "password")
            verify(mockUserPoolAuthenticator).getTokens()
        }
}
