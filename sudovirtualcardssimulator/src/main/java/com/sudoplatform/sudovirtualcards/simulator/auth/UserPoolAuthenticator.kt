/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.auth

import android.content.Context
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserState
import com.amazonaws.mobile.client.UserStateDetails
import com.amazonaws.mobile.client.results.SignInResult
import com.amazonaws.mobile.client.results.SignInState
import com.amazonaws.mobile.client.results.Tokens
import com.amazonaws.mobile.config.AWSConfiguration
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudovirtualcards.simulator.logging.LogConstants
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * An authenticator that uses the [AWSMobileClient] to authenticate via the Cognito UserPool.
 */
internal interface UserPoolAuthenticator {
    enum class State {
        UNKNOWN,
        SIGNED_OUT,
        SIGNED_IN,
    }

    val state: State

    suspend fun initialize()

    suspend fun signIn(
        username: String,
        password: String,
    )

    suspend fun getTokens(): Tokens?
}

// To assist with testing
internal interface MobileClientAuthenticator {
    fun initialize(
        context: Context,
        configuration: AWSConfiguration,
        callback: Callback<UserStateDetails>,
    )

    fun signIn(
        username: String,
        password: String,
        callback: Callback<SignInResult>,
    )

    fun getTokens(callback: Callback<Tokens>)
}

// To assist with testing
private class MobileClientWrapper : MobileClientAuthenticator {
    private val delegate by lazy { AWSMobileClient.getInstance() }

    override fun initialize(
        context: Context,
        configuration: AWSConfiguration,
        callback: Callback<UserStateDetails>,
    ) = delegate.initialize(context, configuration, callback)

    override fun signIn(
        username: String,
        password: String,
        callback: Callback<SignInResult>,
    ) = delegate.signIn(username, password, emptyMap(), callback)

    override fun getTokens(callback: Callback<Tokens>) = delegate.getTokens(callback)
}

internal class AWSUserPoolAuthenticator(
    private val context: Context,
    private val configurationJson: String,
    private val mobileClient: MobileClientAuthenticator = MobileClientWrapper(),
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
) : UserPoolAuthenticator {
    private var actualState: UserPoolAuthenticator.State = UserPoolAuthenticator.State.UNKNOWN

    override val state: UserPoolAuthenticator.State
        get() = actualState

    override suspend fun initialize() =
        suspendCoroutine<Unit> { cont ->

            val configuration = AWSConfiguration(JSONObject(configurationJson))

            mobileClient.initialize(
                context,
                configuration,
                object : Callback<UserStateDetails> {
                    override fun onResult(userState: UserStateDetails?) {
                        userState?.let {
                            actualState =
                                when (it.userState) {
                                    UserState.GUEST,
                                    UserState.SIGNED_IN,
                                    -> UserPoolAuthenticator.State.SIGNED_IN
                                    UserState.SIGNED_OUT_FEDERATED_TOKENS_INVALID,
                                    UserState.SIGNED_OUT_USER_POOLS_TOKENS_INVALID,
                                    UserState.SIGNED_OUT,
                                    -> UserPoolAuthenticator.State.SIGNED_OUT
                                    else -> UserPoolAuthenticator.State.UNKNOWN
                                }
                        }
                        cont.resume(Unit)
                    }

                    override fun onError(e: Exception?) {
                        logger.error("initialize $e")
                        actualState = UserPoolAuthenticator.State.UNKNOWN
                        cont.resumeWithException(e as Throwable)
                    }
                },
            )
        }

    override suspend fun signIn(
        username: String,
        password: String,
    ) = suspendCoroutine<Unit> { cont ->

        mobileClient.signIn(
            username,
            password,
            object : Callback<SignInResult> {
                override fun onResult(result: SignInResult?) {
                    result?.signInState?.let {
                        actualState =
                            when (it) {
                                SignInState.DONE -> UserPoolAuthenticator.State.SIGNED_IN
                                else -> UserPoolAuthenticator.State.UNKNOWN
                            }
                    }
                    cont.resume(Unit)
                }

                override fun onError(e: Exception?) {
                    logger.error("signIn $e")
                    cont.resumeWithException(e as Throwable)
                }
            },
        )
    }

    override suspend fun getTokens() =
        suspendCoroutine<Tokens?> { cont ->

            mobileClient.getTokens(
                object : Callback<Tokens> {
                    override fun onResult(tokens: Tokens?) {
                        cont.resume(tokens)
                    }

                    override fun onError(e: Exception?) {
                        logger.error("getTokens $e")
                        cont.resumeWithException(e as Throwable)
                    }
                },
            )
        }
}
