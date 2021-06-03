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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudovirtualcards.simulator.graphql.CallbackHolder
import com.sudoplatform.sudovirtualcards.simulator.graphql.ListSimulatorConversionRatesQuery
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
 * Test the correct operation of the currency conversion rates in [DefaultSudoVirtualCardsSimulatorClient] using mocks and spies.
 *
 * @since 2020-06-05
 */
class SudoVirtualCardsSimulatorConversionRatesTest : BaseTests() {

    private val holder = CallbackHolder<ListSimulatorConversionRatesQuery.Data>()

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListSimulatorConversionRatesQuery>()) } doReturn holder.queryOperation
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

    @Before
    fun init() {
        holder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockAppSyncClient)
    }

    @Test
    fun `getSimulatorConversionRates() should return results when no error present`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.getSimulatorConversionRates()
        }
        deferredResult.start()
        delay(100L)

        val conversionRates = listOf(
            ListSimulatorConversionRatesQuery.ListSimulatorConversionRate(
                "typename",
                "AUD",
                100_000
            ),
            ListSimulatorConversionRatesQuery.ListSimulatorConversionRate(
                "typename",
                "USD",
                67_294
            )
        )

        val response = Response.builder<ListSimulatorConversionRatesQuery.Data>(ListSimulatorConversionRatesQuery())
            .data(ListSimulatorConversionRatesQuery.Data(conversionRates))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val currencies = deferredResult.await()
        currencies.isEmpty() shouldBe false
        currencies.size shouldBe 2

        with(currencies[0]) {
            currency shouldBe "AUD"
            amount shouldBe 100_000
        }

        verify(mockAppSyncClient).query(any<ListSimulatorConversionRatesQuery>())
    }

    @Test
    fun `getSimulatorConversionRates() should throw when authentication fails`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.AuthenticationException> {
                client.getSimulatorConversionRates()
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onFailure(ApolloException("Failed", RuntimeException("Cognito UserPool failure")))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListSimulatorConversionRatesQuery>())
    }

    @Test
    fun `getSimulatorConversionRates() should throw when network fails`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.FailedException> {
                client.getSimulatorConversionRates()
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onNetworkError(ApolloNetworkException("Mock"))

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListSimulatorConversionRatesQuery>())
    }

    @Test
    fun `getSimulatorConversionRates() should throw when http error occurs`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.FailedException> {
                client.getSimulatorConversionRates()
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

        verify(mockAppSyncClient).query(any<ListSimulatorConversionRatesQuery>())
    }

    @Test
    fun `getSimulatorConversionRates() should throw when random error occurs`() = runBlocking<Unit> {

        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListSimulatorConversionRatesQuery>()) } doThrow RuntimeException("Mock")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.UnknownException> {
                client.getSimulatorConversionRates()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListSimulatorConversionRatesQuery>())
    }

    @Test
    fun `getSimulatorConversionRates() should not suppress CancellationException`() = runBlocking<Unit> {

        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListSimulatorConversionRatesQuery>()) } doThrow CancellationException("Mock")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.getSimulatorConversionRates()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListSimulatorConversionRatesQuery>())
    }

    @Test
    fun `getSimulatorConversionRates() should throw when backend error occurs`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.FailedException> {
                client.getSimulatorConversionRates()
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        val error = Error("mock", emptyList(), mapOf("errorType" to "Mock"))
        val response = Response.builder<ListSimulatorConversionRatesQuery.Data>(ListSimulatorConversionRatesQuery())
            .errors(listOf(error))
            .build()

        holder.callback?.onResponse(response)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListSimulatorConversionRatesQuery>())
    }
}
