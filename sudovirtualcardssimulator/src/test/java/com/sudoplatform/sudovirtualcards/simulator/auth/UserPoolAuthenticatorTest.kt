/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.auth

import android.content.Context
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserState
import com.amazonaws.mobile.client.UserStateDetails
import com.amazonaws.mobile.client.results.SignInResult
import com.amazonaws.mobile.client.results.Tokens
import com.amazonaws.mobile.config.AWSConfiguration
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudovirtualcards.simulator.BaseTests
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.lang.NullPointerException

/**
 * Test the correct operation of the [AWSUserPoolAuthenticator] using mocks and spies.
 */
class UserPoolAuthenticatorTest : BaseTests() {

    private val mockContext by before {
        mock<Context>()
    }

    private var userState: UserState? = UserState.UNKNOWN
    private var initializeError: Exception? = null
    private var signInError: Exception? = null
    private var getTokensError: Exception? = null

    private val mockMobileClientAuthenticator = object : MobileClientAuthenticator {
        override fun initialize(context: Context, configuration: AWSConfiguration, callback: Callback<UserStateDetails>) {
            if (initializeError != null) {
                callback.onError(initializeError)
            } else if (userState != null) {
                callback.onResult(UserStateDetails(userState, emptyMap()))
            } else {
                fail("Missing initialize state")
            }
        }

        override fun signIn(username: String, password: String, callback: Callback<SignInResult>) {
            if (signInError != null) {
                callback.onError(signInError)
            } else {
                callback.onResult(SignInResult.DONE)
            }
        }

        override fun getTokens(callback: Callback<Tokens>) {
            if (getTokensError != null) {
                callback.onError(getTokensError)
            } else {
                callback.onResult(Tokens("access", "id", "refresh"))
            }
        }
    }

    private val configJson = """{ "foo":"bar" }"""

    private val mockLogger by before {
        Logger("mock", mock<LogDriverInterface>())
    }

    private val authenticator by before {
        AWSUserPoolAuthenticator(
            context = mockContext,
            configurationJson = configJson,
            mobileClient = mockMobileClientAuthenticator,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext)
    }

    @Test
    fun `initialize, signIn, getTokens should call aws client`() = runBlocking<Unit> {
        // given
        userState = UserState.SIGNED_OUT
        authenticator.state shouldBe UserPoolAuthenticator.State.UNKNOWN

        // when
        authenticator.initialize()

        // then
        authenticator.state shouldBe UserPoolAuthenticator.State.SIGNED_OUT

        // when
        authenticator.signIn("", "")

        // then
        authenticator.state shouldBe UserPoolAuthenticator.State.SIGNED_IN

        // when
        val tokens = authenticator.getTokens()
        tokens shouldNotBe null
        with(tokens!!) {
            accessToken.tokenString shouldBe "access"
            idToken.tokenString shouldBe "id"
            refreshToken.tokenString shouldBe "refresh"
        }

        // then
        authenticator.state shouldBe UserPoolAuthenticator.State.SIGNED_IN
    }

    @Test
    fun `initialize should throw when aws client reports error`() = runBlocking<Unit> {
        // given
        initializeError = NullPointerException("Mock")

        // when
        shouldThrow<NullPointerException> {
            authenticator.initialize()
        }
    }

    @Test
    fun `signIn should throw when aws client reports error`() = runBlocking<Unit> {
        // given
        signInError = NullPointerException("Mock")

        // when
        shouldThrow<NullPointerException> {
            authenticator.signIn("", "")
        }
    }

    @Test
    fun `getTokens should throw when aws client reports error`() = runBlocking<Unit> {
        // given
        getTokensError = NullPointerException("Mock")

        // when
        shouldThrow<NullPointerException> {
            authenticator.getTokens()
        }
    }
}
