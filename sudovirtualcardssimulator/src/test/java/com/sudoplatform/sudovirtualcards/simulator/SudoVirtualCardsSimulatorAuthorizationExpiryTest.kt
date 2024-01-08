/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudovirtualcards.simulator.graphql.CallbackHolder
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateAuthorizationExpiryMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateAuthorizationExpiryRequest
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.net.HttpURLConnection

/**
 * Test the correct operation of the authorization expiry in [DefaultSudoVirtualCardsSimulatorClient] using mocks and spies.
 */
class SudoVirtualCardsSimulatorAuthorizationExpiryTest : BaseTests() {

    private val holder = CallbackHolder<SimulateAuthorizationExpiryMutation.Data>()

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<SimulateAuthorizationExpiryMutation>()) } doReturn holder.mutationOperation
        }
    }

    private val mockLogger by before {
        val mockLogDriver = mock<LogDriverInterface>().stub {
            on { logLevel } doReturn LogLevel.NONE
        }
        Logger("mock", mockLogDriver)
    }

    private val client by before {
        SudoVirtualCardsSimulatorClient.builder()
            .setAppSyncClient(mockAppSyncClient)
            .setLogger(mockLogger)
            .build()
    }

    private val authorizationId = "authId"

    @Before
    fun init() {
        holder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockAppSyncClient)
    }

    @Test
    fun `simulateAuthorizationExpiry() should return results when no error present`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            client.simulateAuthorizationExpiry(authorizationId)
        }
        deferredAuthorization.start()
        delay(100L)

        val rawResponse = SimulateAuthorizationExpiryMutation.SimulateAuthorizationExpiry(
            "typename",
            "id",
            1.0,
            1.0,
        )

        val req = SimulateAuthorizationExpiryRequest.builder()
            .authorizationId(authorizationId)
            .build()
        val response = Response.builder<SimulateAuthorizationExpiryMutation.Data>(SimulateAuthorizationExpiryMutation(req))
            .data(SimulateAuthorizationExpiryMutation.Data(rawResponse))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val authorization = deferredAuthorization.await()
        authorization shouldNotBe null

        with(authorization) {
            id shouldBe "id"
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
        }

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationExpiryMutation>())
    }

    @Test
    fun `simulateAuthorizationExpiry() should throw when authentication fails`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.AuthenticationException> {
                client.simulateAuthorizationExpiry(authorizationId)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onFailure(ApolloException("Failed", RuntimeException("Cognito UserPool failure")))

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationExpiryMutation>())
    }

    @Test
    fun `simulateAuthorizationExpiry() should throw when network fails`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException> {
                client.simulateAuthorizationExpiry(authorizationId)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onNetworkError(ApolloNetworkException("Mock"))

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationExpiryMutation>())
    }

    @Test
    fun `simulateAuthorizationExpiry() should throw when http error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException> {
                client.simulateAuthorizationExpiry(authorizationId)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        val request = okhttp3.Request.Builder()
            .get()
            .url("http://www.smh.com.au")
            .build()
        val responseBody = "{}".toResponseBody("application/json; charset=utf-8".toMediaType())
        val forbidden = okhttp3.Response.Builder()
            .protocol(Protocol.HTTP_1_1)
            .code(HttpURLConnection.HTTP_FORBIDDEN)
            .request(request)
            .message("Forbidden")
            .body(responseBody)
            .build()
        holder.callback shouldNotBe null
        holder.callback?.onHttpError(ApolloHttpException(forbidden))

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationExpiryMutation>())
    }

    @Test
    fun `simulateAuthorizationExpiry() should throw when random error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SimulateAuthorizationExpiryMutation>()) } doThrow RuntimeException("Mock")
        }

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.UnknownException> {
                client.simulateAuthorizationExpiry(authorizationId)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationExpiryMutation>())
    }

    @Test
    fun `simulateAuthorizationExpiry() should not suppress CancellationException`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SimulateAuthorizationExpiryMutation>()) } doThrow CancellationException("Mock")
        }

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.simulateAuthorizationExpiry(authorizationId)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationExpiryMutation>())
    }

    @Test
    fun `simulateAuthorizationExpiry() should throw when backend error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException> {
                client.simulateAuthorizationExpiry(authorizationId)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        val req = SimulateAuthorizationExpiryRequest.builder()
            .authorizationId("000001")
            .build()

        val error = Error("mock", emptyList(), mapOf("errorType" to "Mock"))
        val response = Response.builder<SimulateAuthorizationExpiryMutation.Data>(SimulateAuthorizationExpiryMutation(req))
            .errors(listOf(error))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationExpiryMutation>())
    }
}
