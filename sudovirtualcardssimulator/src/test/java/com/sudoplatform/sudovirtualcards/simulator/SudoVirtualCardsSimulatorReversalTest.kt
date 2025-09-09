/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.amplify.GraphQLClient
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateReversalMutation
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.net.HttpURLConnection

/**
 * Test the correct operation of the reversals in [DefaultSudoVirtualCardsSimulatorClient] using mocks and spies.
 */
class SudoVirtualCardsSimulatorReversalTest : BaseTests() {
    private val mutationResponse by before {
        JSONObject(
            """
            {
                'simulateReversal': {
                    'id':'id',
                    'billedAmount': {
                        'currency': 'currency',
                        'amount': 10000
                    },
                    'createdAtEpochMs': 1.0,
                    'updatedAtEpochMs': 1.0
                }
            }
            """.trimIndent(),
        )
    }
    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(SimulateReversalMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                val mockOperation: GraphQLOperation<String> = mock()
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationResponse.toString(), null),
                )
                mockOperation
            }
        }
    }

    private val mockLogger by before {
        val mockLogDriver =
            mock<LogDriverInterface>().stub {
                on { logLevel } doReturn LogLevel.NONE
            }
        Logger("mock", mockLogDriver)
    }

    private val client by before {
        SudoVirtualCardsSimulatorClient
            .builder()
            .setGraphQLClient(GraphQLClient(mockApiCategory))
            .setLogger(mockLogger)
            .build()
    }

    private val request =
        SimulateReversalInput(
            "authId",
            10_000,
        )

    @After
    fun fini() {
        verifyNoMoreInteractions(mockApiCategory)
    }

    @Test
    fun `simulateReversal() should return results when no error present`() =
        runBlocking<Unit> {
            val deferredResult =
                async(Dispatchers.IO) {
                    client.simulateReversal(request)
                }
            deferredResult.start()
            delay(100L)
            val debit = deferredResult.await()
            debit shouldNotBe null

            with(debit) {
                id shouldBe "id"
                amount shouldBe 10_000
                currency shouldBe "currency"
                createdAt.time shouldBeGreaterThan 0L
                updatedAt.time shouldBeGreaterThan 0L
            }

            verify(mockApiCategory).mutate<String>(
                check {
                    assertEquals(SimulateReversalMutation.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `simulateReversal() should throw when authentication fails`() =
        runBlocking<Unit> {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(SimulateReversalMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow RuntimeException("Cognito UserPool failure")
            }

            val deferredResult =
                async(Dispatchers.IO) {
                    shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.AuthenticationException> {
                        client.simulateReversal(request)
                    }
                }
            deferredResult.start()
            delay(100L)
            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    assertEquals(SimulateReversalMutation.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `simulateReversal() should throw when http error occurs`() =
        runBlocking<Unit> {
            val errors =
                listOf(
                    GraphQLResponse.Error(
                        "mock",
                        null,
                        null,
                        mapOf("httpStatus" to HttpURLConnection.HTTP_INTERNAL_ERROR),
                    ),
                )
            val mockOperation: GraphQLOperation<String> = mock()
            whenever(
                mockApiCategory.mutate<String>(
                    argThat { this.query.equals(SimulateReversalMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                ),
            ).thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, errors),
                )
                mockOperation
            }
            val deferredResult =
                async(Dispatchers.IO) {
                    shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.FailedException> {
                        client.simulateReversal(request)
                    }
                }
            deferredResult.start()
            delay(100L)

            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    assertEquals(SimulateReversalMutation.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `simulateReversal() should throw when random error occurs`() =
        runBlocking<Unit> {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(SimulateReversalMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow RuntimeException("Mock")
            }

            val deferredResult =
                async(Dispatchers.IO) {
                    shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.UnknownException> {
                        client.simulateReversal(request)
                    }
                }
            deferredResult.start()
            delay(100L)

            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    assertEquals(SimulateReversalMutation.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `simulateReversal() should not suppress CancellationException`() =
        runBlocking<Unit> {
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(SimulateReversalMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow CancellationException("Mock")
            }
            val deferredResult =
                async(Dispatchers.IO) {
                    shouldThrow<CancellationException> {
                        client.simulateReversal(request)
                    }
                }
            deferredResult.start()
            delay(100L)

            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    assertEquals(SimulateReversalMutation.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `simulateReversal() should throw when backend error occurs`() =
        runBlocking<Unit> {
            val errors =
                listOf(
                    GraphQLResponse.Error(
                        "mock",
                        null,
                        null,
                        mapOf("errorType" to "TransactionNotFoundError"),
                    ),
                )
            val mockOperation: GraphQLOperation<String> = mock()
            whenever(
                mockApiCategory.mutate<String>(
                    argThat { this.query.equals(SimulateReversalMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                ),
            ).thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, errors),
                )
                mockOperation
            }
            val deferredResult =
                async(Dispatchers.IO) {
                    shouldThrow<SudoVirtualCardsSimulatorClient.ReversalException.AuthorizationNotFoundException> {
                        client.simulateReversal(request)
                    }
                }
            deferredResult.start()
            delay(100L)

            deferredResult.await()

            verify(mockApiCategory).mutate<String>(
                check {
                    assertEquals(SimulateReversalMutation.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }
}
