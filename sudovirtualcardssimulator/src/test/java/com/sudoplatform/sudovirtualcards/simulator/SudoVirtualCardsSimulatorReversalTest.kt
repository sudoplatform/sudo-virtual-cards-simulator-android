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
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateReversalMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateReversalRequest
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateReversalInput
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
 * Test the correct operation of the reversals in [DefaultSudoVirtualCardsSimulatorClient] using mocks and spies.
 */
class SudoVirtualCardsSimulatorReversalTest : BaseTests() {

    private val holder = CallbackHolder<SimulateReversalMutation.Data>()

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<SimulateReversalMutation>()) } doReturn holder.mutationOperation
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

    private val request = SimulateReversalInput(
        "authId",
        10_000,
    )

    @Before
    fun init() {
        holder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockAppSyncClient)
    }

    @Test
    fun `simulateReversal() should return results when no error present`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.simulateReversal(request)
        }
        deferredResult.start()
        delay(100L)

        val rawResponse = SimulateReversalMutation.SimulateReversal(
            "typename",
            "id",
            SimulateReversalMutation.BilledAmount("typename", "currency", 10_000),
            1.0,
            1.0,
        )

        val req = SimulateReversalRequest.builder()
            .authorizationId("authId")
            .amount(10_000)
            .build()
        val response = Response.builder<SimulateReversalMutation.Data>(SimulateReversalMutation(req))
            .data(SimulateReversalMutation.Data(rawResponse))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val debit = deferredResult.await()
        debit shouldNotBe null

        with(debit) {
            id shouldBe "id"
            amount shouldBe 10_000
            currency shouldBe "currency"
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
        }

        verify(mockAppSyncClient).mutate(any<SimulateReversalMutation>())
    }

    @Test
    fun `simulateReversal() should throw when authentication fails`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.AuthenticationException> {
                client.simulateReversal(request)
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onFailure(ApolloException("Failed", RuntimeException("Cognito UserPool failure")))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateReversalMutation>())
    }

    @Test
    fun `simulateReversal() should throw when network fails`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.FailedException> {
                client.simulateReversal(request)
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onNetworkError(ApolloNetworkException("Mock"))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateReversalMutation>())
    }

    @Test
    fun `simulateReversal() should throw when http error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.FailedException> {
                client.simulateReversal(request)
            }
        }
        deferredResult.start()
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

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateReversalMutation>())
    }

    @Test
    fun `simulateReversal() should throw when random error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SimulateReversalMutation>()) } doThrow RuntimeException("Mock")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.UnknownException> {
                client.simulateReversal(request)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateReversalMutation>())
    }

    @Test
    fun `simulateReversal() should not suppress CancellationException`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SimulateReversalMutation>()) } doThrow CancellationException("Mock")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.simulateReversal(request)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateReversalMutation>())
    }

    @Test
    fun `simulateReversal() should throw when backend error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.AuthorizationNotFoundException> {
                client.simulateReversal(request)
            }
        }
        deferredResult.start()
        delay(100L)

        val req = SimulateReversalRequest.builder()
            .authorizationId("authId")
            .amount(10_000)
            .build()

        val error = Error("mock", emptyList(), mapOf("errorType" to "TransactionNotFoundError"))
        val response = Response.builder<SimulateReversalMutation.Data>(SimulateReversalMutation(req))
            .errors(listOf(error))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateReversalMutation>())
    }
}
