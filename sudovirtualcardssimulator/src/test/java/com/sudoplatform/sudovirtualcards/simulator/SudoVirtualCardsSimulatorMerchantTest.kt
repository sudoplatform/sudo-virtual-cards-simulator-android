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
import com.sudoplatform.sudovirtualcards.simulator.graphql.ListSimulatorMerchantsQuery
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
import java.util.Date

/**
 * Test the correct operation of the merchants in [DefaultSudoVirtualCardsSimulatorClient] using mocks and spies.
 */
class SudoVirtualCardsSimulatorMerchantTest : BaseTests() {

    private val holder = CallbackHolder<ListSimulatorMerchantsQuery.Data>()

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListSimulatorMerchantsQuery>()) } doReturn holder.queryOperation
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
    fun `getSimulatorMerchants() should return results when no error present`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredMerchants = async(Dispatchers.IO) {
            client.getSimulatorMerchants()
        }
        deferredMerchants.start()
        delay(100L)

        val rawMerchants = listOf(
            ListSimulatorMerchantsQuery.ListSimulatorMerchant(
                "typename",
                "id",
                "description",
                "name",
                "mcc",
                "city",
                "state",
                "postcode",
                "country",
                "currency",
                true,
                false,
                1.0,
                2.0
            )
        )

        val response = Response.builder<ListSimulatorMerchantsQuery.Data>(ListSimulatorMerchantsQuery())
            .data(ListSimulatorMerchantsQuery.Data(rawMerchants))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val merchants = deferredMerchants.await()
        merchants.isEmpty() shouldBe false
        merchants.size shouldBe 1

        with(merchants[0]) {
            id shouldBe "id"
            description shouldBe "description"
            name shouldBe "name"
            mcc shouldBe "mcc"
            city shouldBe "city"
            state shouldBe "state"
            postalCode shouldBe "postcode"
            country shouldBe "country"
            currency shouldBe "currency"
            declineAfterAuthorization shouldBe true
            declineBeforeAuthorization shouldBe false
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(2L)
        }

        verify(mockAppSyncClient).query(any<ListSimulatorMerchantsQuery>())
    }

    @Test
    fun `getSimulatorMerchants() should throw when authentication fails`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredMerchants = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.AuthenticationException> {
                client.getSimulatorMerchants()
            }
        }
        deferredMerchants.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onFailure(ApolloException("Failed", RuntimeException("Cognito UserPool failure")))

        deferredMerchants.await()

        verify(mockAppSyncClient).query(any<ListSimulatorMerchantsQuery>())
    }

    @Test
    fun `getSimulatorMerchants() should throw when network fails`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredMerchants = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.FailedException> {
                client.getSimulatorMerchants()
            }
        }
        deferredMerchants.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onNetworkError(ApolloNetworkException("Mock"))

        deferredMerchants.await()

        verify(mockAppSyncClient).query(any<ListSimulatorMerchantsQuery>())
    }

    @Test
    fun `getSimulatorMerchants() should throw when http error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredMerchants = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.FailedException> {
                client.getSimulatorMerchants()
            }
        }
        deferredMerchants.start()
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

        deferredMerchants.await()

        verify(mockAppSyncClient).query(any<ListSimulatorMerchantsQuery>())
    }

    @Test
    fun `getSimulatorMerchants() should throw when random error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListSimulatorMerchantsQuery>()) } doThrow RuntimeException("Mock")
        }

        val deferredMerchants = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.UnknownException> {
                client.getSimulatorMerchants()
            }
        }
        deferredMerchants.start()
        delay(100L)

        deferredMerchants.await()

        verify(mockAppSyncClient).query(any<ListSimulatorMerchantsQuery>())
    }

    @Test
    fun `getSimulatorMerchants() should not suppress CancellationException`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListSimulatorMerchantsQuery>()) } doThrow CancellationException("Mock")
        }

        val deferredMerchants = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.getSimulatorMerchants()
            }
        }
        deferredMerchants.start()
        delay(100L)

        deferredMerchants.await()

        verify(mockAppSyncClient).query(any<ListSimulatorMerchantsQuery>())
    }

    @Test
    fun `getSimulatorMerchants() should throw when backend error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredMerchants = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.FailedException> {
                client.getSimulatorMerchants()
            }
        }
        deferredMerchants.start()
        delay(100L)

        holder.callback shouldNotBe null
        val error = Error("mock", emptyList(), mapOf("errorType" to "Mock"))
        val response = Response.builder<ListSimulatorMerchantsQuery.Data>(ListSimulatorMerchantsQuery())
            .errors(listOf(error))
            .build()

        holder.callback?.onResponse(response)

        deferredMerchants.await()

        verify(mockAppSyncClient).query(any<ListSimulatorMerchantsQuery>())
    }
}
