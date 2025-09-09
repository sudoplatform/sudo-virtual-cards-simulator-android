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
import com.sudoplatform.sudovirtualcards.simulator.graphql.ListSimulatorMerchantsQuery
import io.kotlintest.shouldBe
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
import java.util.Date

/**
 * Test the correct operation of the merchants in [DefaultSudoVirtualCardsSimulatorClient] using mocks and spies.
 */
class SudoVirtualCardsSimulatorMerchantTest : BaseTests() {
    private val queryResponse by before {
        JSONObject(
            """
            {
                'listSimulatorMerchants': [{
                    'id':'id',
                    'description': 'description',
                    'name': 'name',
                    'mcc': 'mcc',
                    'city': 'city',
                    'state': 'state',
                    'postalCode': 'postcode',
                    'country': 'country',
                    'currency': 'currency',
                    'declineAfterAuthorization': true,
                    'declineBeforeAuthorization': false,
                    'createdAtEpochMs': 1.0,
                    'updatedAtEpochMs': 2.0
                }]
            }
            """.trimIndent(),
        )
    }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                query<String>(
                    argThat { this.query.equals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                val mockOperation: GraphQLOperation<String> = mock()
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(queryResponse.toString(), null),
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

    @After
    fun fini() {
        verifyNoMoreInteractions(mockApiCategory)
    }

    @Test
    fun `getSimulatorMerchants() should return results when no error present`() =
        runBlocking<Unit> {
            val deferredMerchants =
                async(Dispatchers.IO) {
                    client.getSimulatorMerchants()
                }
            deferredMerchants.start()
            delay(100L)

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

            verify(mockApiCategory).query<String>(
                check {
                    assertEquals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `getSimulatorMerchants() should throw when authentication fails`() =
        runBlocking<Unit> {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow RuntimeException("Cognito UserPool failure")
            }
            val deferredMerchants =
                async(Dispatchers.IO) {
                    shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.AuthenticationException> {
                        client.getSimulatorMerchants()
                    }
                }
            deferredMerchants.start()
            delay(100L)

            deferredMerchants.await()

            verify(mockApiCategory).query<String>(
                check {
                    assertEquals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `getSimulatorMerchants() should throw when http error occurs`() =
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
                mockApiCategory.query<String>(
                    argThat { this.query.equals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT) },
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
            val deferredMerchants =
                async(Dispatchers.IO) {
                    shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.FailedException> {
                        client.getSimulatorMerchants()
                    }
                }
            deferredMerchants.start()
            delay(100L)

            deferredMerchants.await()

            verify(mockApiCategory).query<String>(
                check {
                    assertEquals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `getSimulatorMerchants() should throw when random error occurs`() =
        runBlocking<Unit> {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow RuntimeException("Mock")
            }

            val deferredMerchants =
                async(Dispatchers.IO) {
                    shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.UnknownException> {
                        client.getSimulatorMerchants()
                    }
                }
            deferredMerchants.start()
            delay(100L)

            deferredMerchants.await()

            verify(mockApiCategory).query<String>(
                check {
                    assertEquals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `getSimulatorMerchants() should not suppress CancellationException`() =
        runBlocking<Unit> {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doThrow CancellationException("Mock")
            }

            val deferredMerchants =
                async(Dispatchers.IO) {
                    shouldThrow<CancellationException> {
                        client.getSimulatorMerchants()
                    }
                }
            deferredMerchants.start()
            delay(100L)

            deferredMerchants.await()

            verify(mockApiCategory).query<String>(
                check {
                    assertEquals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }

    @Test
    fun `getSimulatorMerchants() should throw when backend error occurs`() =
        runBlocking<Unit> {
            val errors =
                listOf(
                    GraphQLResponse.Error(
                        "mock",
                        null,
                        null,
                        mapOf("errorType" to "Mock"),
                    ),
                )
            val mockOperation: GraphQLOperation<String> = mock()
            whenever(
                mockApiCategory.query<String>(
                    argThat { this.query.equals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT) },
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
            val deferredMerchants =
                async(Dispatchers.IO) {
                    shouldThrow<SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.FailedException> {
                        client.getSimulatorMerchants()
                    }
                }
            deferredMerchants.start()
            delay(100L)

            deferredMerchants.await()

            verify(mockApiCategory).query<String>(
                check {
                    assertEquals(ListSimulatorMerchantsQuery.OPERATION_DOCUMENT, it.query)
                },
                any(),
                any(),
            )
        }
}
