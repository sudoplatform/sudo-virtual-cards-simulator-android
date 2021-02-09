/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudovirtualcards.simulator.graphql.CallbackHolder
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateRefundMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateRefundRequest
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateRefundInput
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
import java.net.HttpURLConnection

/**
 * Test the correct operation of the refunds in [DefaultSudoVirtualCardsSimulatorClient] using mocks and spies.
 *
 * @since 2020-07-01
 */
class SudoVirtualCardsSimulatorRefundTest : BaseTests() {

    private val holder = CallbackHolder<SimulateRefundMutation.Data>()

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<SimulateRefundMutation>()) } doReturn holder.mutationOperation
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

    private val request = SimulateRefundInput(
        "debitId",
        10_000
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
    fun `simulateRefund() should return results when no error present`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.simulateRefund(request)
        }
        deferredResult.start()
        delay(100L)

        val rawResponse = SimulateRefundMutation.SimulateRefund(
            "typename",
            "id",
            SimulateRefundMutation.BilledAmount("typename", "currency", 10_000),
            1.0,
            1.0
        )

        val req = SimulateRefundRequest.builder()
            .debitId("debitId")
            .amount(10_000)
            .build()
        val response = Response.builder<SimulateRefundMutation.Data>(SimulateRefundMutation(req))
            .data(SimulateRefundMutation.Data(rawResponse))
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

        verify(mockAppSyncClient).mutate(any<SimulateRefundMutation>())
    }

    @Test
    fun `simulateRefund() should throw when authentication fails`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.RefundException.AuthenticationException> {
                client.simulateRefund(request)
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onFailure(ApolloException("Failed", RuntimeException("Cognito UserPool failure")))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateRefundMutation>())
    }

    @Test
    fun `simulateRefund() should throw when network fails`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.RefundException.FailedException> {
                client.simulateRefund(request)
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onNetworkError(ApolloNetworkException("Mock"))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateRefundMutation>())
    }

    @Test
    fun `simulateRefund() should throw when http error occurs`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.RefundException.FailedException> {
                client.simulateRefund(request)
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

        verify(mockAppSyncClient).mutate(any<SimulateRefundMutation>())
    }

    @Test
    fun `simulateRefund() should throw when random error occurs`() = runBlocking<Unit> {

        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SimulateRefundMutation>()) } doThrow RuntimeException("Mock")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.RefundException.UnknownException> {
                client.simulateRefund(request)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateRefundMutation>())
    }

    @Test
    fun `simulateRefund() should not suppress CancellationException`() = runBlocking<Unit> {

        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SimulateRefundMutation>()) } doThrow CancellationException("Mock")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.simulateRefund(request)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateRefundMutation>())
    }

    @Test
    fun `simulateRefund() should throw when backend error occurs`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.RefundException.DebitNotFoundException> {
                client.simulateRefund(request)
            }
        }
        deferredResult.start()
        delay(100L)

        val req = SimulateRefundRequest.builder()
            .debitId("debitId")
            .amount(10_000)
            .build()

        val error = Error("mock", emptyList(), mapOf("errorType" to "TransactionNotFoundError"))
        val response = Response.builder<SimulateRefundMutation.Data>(SimulateRefundMutation(req))
            .errors(listOf(error))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SimulateRefundMutation>())
    }
}
