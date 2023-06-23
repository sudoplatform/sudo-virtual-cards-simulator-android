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
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateAuthorizationMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.ExpiryInput
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateAuthorizationRequest
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.BillingAddress
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateAuthorizationInput
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
 * Test the correct operation of the authorizations in [DefaultSudoVirtualCardsSimulatorClient] using mocks and spies.
 */
class SudoVirtualCardsSimulatorAuthorizationTest : BaseTests() {

    private val holder = CallbackHolder<SimulateAuthorizationMutation.Data>()

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<SimulateAuthorizationMutation>()) } doReturn holder.mutationOperation
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

    private val billingAddress = BillingAddress(
        addressLine1 = TestData.VerifiedUser.addressLine1,
        city = TestData.VerifiedUser.city,
        postalCode = TestData.VerifiedUser.postalCode,
        state = TestData.VerifiedUser.state,
        country = TestData.VerifiedUser.country
    )

    private val request = SimulateAuthorizationInput(
        "cardNumber",
        10_000,
        "merchantId",
        1,
        2021,
        billingAddress,
        "securityCode"
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
    fun `simulateAuthorization() should return results when no error present`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            client.simulateAuthorization(request)
        }
        deferredAuthorization.start()
        delay(100L)

        val rawResponse = SimulateAuthorizationMutation.SimulateAuthorization(
            "typename",
            "id",
            true,
            SimulateAuthorizationMutation.BilledAmount("typename", "currency", 10_000),
            null,
            1.0,
            1.0
        )

        val expiry = ExpiryInput.builder()
            .mm(1)
            .yyyy(2030)
            .build()
        val req = SimulateAuthorizationRequest.builder()
            .amount(10_000)
            .merchantId("000001")
            .expiry(expiry)
            .pan("42")
            .build()
        val response = Response.builder<SimulateAuthorizationMutation.Data>(SimulateAuthorizationMutation(req))
            .data(SimulateAuthorizationMutation.Data(rawResponse))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val authorization = deferredAuthorization.await()
        authorization shouldNotBe null

        with(authorization) {
            id shouldBe "id"
            isApproved shouldBe true
            amount shouldBe 10_000
            currency shouldBe "currency"
            declineReason shouldBe null
            createdAt.time shouldBeGreaterThan 0L
            updatedAt.time shouldBeGreaterThan 0L
        }

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationMutation>())
    }

    @Test
    fun `simulateAuthorization() should throw when authentication fails`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.AuthenticationException> {
                client.simulateAuthorization(request)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onFailure(ApolloException("Failed", RuntimeException("Cognito UserPool failure")))

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationMutation>())
    }

    @Test
    fun `simulateAuthorization() should throw when network fails`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException> {
                client.simulateAuthorization(request)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onNetworkError(ApolloNetworkException("Mock"))

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationMutation>())
    }

    @Test
    fun `simulateAuthorization() should throw when http error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException> {
                client.simulateAuthorization(request)
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

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationMutation>())
    }

    @Test
    fun `simulateAuthorization() should throw when random error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SimulateAuthorizationMutation>()) } doThrow RuntimeException("Mock")
        }

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.UnknownException> {
                client.simulateAuthorization(request)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationMutation>())
    }

    @Test
    fun `simulateAuthorization() should not suppress CancellationException`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<SimulateAuthorizationMutation>()) } doThrow CancellationException("Mock")
        }

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.simulateAuthorization(request)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationMutation>())
    }

    @Test
    fun `simulateAuthorization() should throw when backend error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredAuthorization = async(Dispatchers.IO) {
            shouldThrow<SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException> {
                client.simulateAuthorization(request)
            }
        }
        deferredAuthorization.start()
        delay(100L)

        val expiry = ExpiryInput.builder()
            .mm(1)
            .yyyy(2030)
            .build()
        val req = SimulateAuthorizationRequest.builder()
            .amount(10_000)
            .merchantId("000001")
            .expiry(expiry)
            .pan("42")
            .build()

        val error = Error("mock", emptyList(), mapOf("errorType" to "Mock"))
        val response = Response.builder<SimulateAuthorizationMutation.Data>(SimulateAuthorizationMutation(req))
            .errors(listOf(error))
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        deferredAuthorization.await()

        verify(mockAppSyncClient).mutate(any<SimulateAuthorizationMutation>())
    }
}
