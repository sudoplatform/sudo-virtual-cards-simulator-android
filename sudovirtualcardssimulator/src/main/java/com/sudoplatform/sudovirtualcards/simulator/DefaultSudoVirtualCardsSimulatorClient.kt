/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.amplify.GraphQLClient
import com.sudoplatform.sudouser.exceptions.GRAPHQL_ERROR_TYPE
import com.sudoplatform.sudouser.exceptions.HTTP_STATUS_CODE_KEY
import com.sudoplatform.sudovirtualcards.simulator.graphql.ListSimulatorConversionRatesQuery
import com.sudoplatform.sudovirtualcards.simulator.graphql.ListSimulatorMerchantsQuery
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateAuthorizationExpiryMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateAuthorizationMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateDebitMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateIncrementalAuthorizationMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateRefundMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateReversalMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.EnteredAddressInput
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.ExpiryInput
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateAuthorizationExpiryRequest
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateAuthorizationRequest
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateDebitRequest
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateIncrementalAuthorizationRequest
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateRefundRequest
import com.sudoplatform.sudovirtualcards.simulator.graphql.type.SimulateReversalRequest
import com.sudoplatform.sudovirtualcards.simulator.logging.LogConstants
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateAuthorizationInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateDebitInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateIncrementalAuthorizationInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateRefundInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateReversalInput
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.CurrencyAmount
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateAuthorizationExpiryResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateAuthorizationResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateDebitResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateRefundResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateReversalResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulatorMerchant
import com.sudoplatform.sudovirtualcards.simulator.types.transformers.SudoVirtualCardsSimulatorTransformer
import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection

// Exception messages
private const val NO_SERVER_RESPONSE = "No response from server"
private const val AUTHORIZATION_NOT_FOUND = "Authorization not found"

// Errors returned from the backend
private const val ERROR_TRANSACTION_NOT_FOUND = "TransactionNotFoundError"
private const val ERROR_CARD_NOT_FOUND = "CardNotFoundError"
private const val ERROR_EXCESSIVE_REVERSAL = "ExcessiveReversalError"
private const val ERROR_EXCESSIVE_REFUND = "ExcessiveRefundError"
private const val ERROR_ALREADY_EXPIRED = "AlreadyExpiredError"

/**
 * Default implementation of the [SudoVirtualCardsSimulatorClient] interface.
 *
 * @property graphQLClient [GraphQLClient] GraphQL client used to make requests to AWS and call sudo virtual cards service API.
 * @property logger [Logger] Errors and warnings will be logged here
 */
internal class DefaultSudoVirtualCardsSimulatorClient(
    private val graphQLClient: GraphQLClient,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
) : SudoVirtualCardsSimulatorClient {
    /**
     * Checksums for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code. We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry. The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "11.0.0"

    override suspend fun getSimulatorMerchants(): List<SimulatorMerchant> {
        try {
            val queryResponse =
                graphQLClient.query<ListSimulatorMerchantsQuery, ListSimulatorMerchantsQuery.Data>(
                    ListSimulatorMerchantsQuery.OPERATION_DOCUMENT,
                    emptyMap(),
                )

            // Check if there was an error within the server
            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors}")
                throw interpretGetMerchantsError(queryResponse.errors.first())
            }

            logger.verbose("${queryResponse.data?.listSimulatorMerchants?.size ?: 0} merchants returned")

            // Iterate over results and build merchants
            val results = queryResponse.data?.listSimulatorMerchants ?: emptyList()
            return SudoVirtualCardsSimulatorTransformer.buildMerchantsFromQueryResults(results)
        } catch (e: Throwable) {
            logger.debug("error $e")
            if (e.isAuthenticationFailure()) {
                throw SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.AuthenticationException(e.message)
            }
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException,
                -> throw e
                else -> throw SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun getSimulatorConversionRates(): List<CurrencyAmount> {
        try {
            val queryResponse =
                graphQLClient.query<ListSimulatorConversionRatesQuery, ListSimulatorConversionRatesQuery.Data>(
                    ListSimulatorConversionRatesQuery.OPERATION_DOCUMENT,
                    emptyMap(),
                )

            val rateCount = queryResponse.data?.listSimulatorConversionRates?.size ?: 0
            logger.verbose("$rateCount rates returned")

            // Check if there was an error within the server
            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors}")
                throw interpretGetConversionRatesError(
                    queryResponse.errors.first(),
                )
            }

            // Iterate over results and build merchants
            val results = queryResponse.data?.listSimulatorConversionRates ?: emptyList()
            return SudoVirtualCardsSimulatorTransformer.buildCurrenciesFromQueryResults(results)
        } catch (e: Throwable) {
            logger.debug("error $e")
            if (e.isAuthenticationFailure()) {
                throw SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.AuthenticationException(e.message)
            }
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException,
                -> throw e
                else -> throw SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateAuthorization(request: SimulateAuthorizationInput): SimulateAuthorizationResponse {
        try {
            val expiry =
                ExpiryInput(
                    mm = request.expirationMonth,
                    yyyy = request.expirationYear,
                )

            var address: EnteredAddressInput? = null
            if (request.billingAddress != null) {
                with(request.billingAddress) {
                    address =
                        EnteredAddressInput(
                            addressLine1 = Optional.presentIfNotNull(addressLine1),
                            addressLine2 = Optional.presentIfNotNull(addressLine2),
                            city = Optional.presentIfNotNull(city),
                            state = Optional.presentIfNotNull(state),
                            country = Optional.presentIfNotNull(country),
                            postalCode = Optional.presentIfNotNull(postalCode),
                        )
                }
            }
            val mutationInput =
                SimulateAuthorizationRequest(
                    amount = request.amount,
                    pan = request.cardNumber,
                    merchantId = request.merchantId,
                    csc = Optional.presentIfNotNull(request.securityCode),
                    expiry = expiry,
                    billingAddress = Optional.presentIfNotNull(address),
                )

            val mutationResponse =
                graphQLClient.mutate<SimulateAuthorizationMutation, SimulateAuthorizationMutation.Data>(
                    SimulateAuthorizationMutation.OPERATION_DOCUMENT,
                    mapOf("input" to mutationInput),
                )

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretAuthorizeError(mutationResponse.errors.first())
            }

            logger.verbose("approved=${mutationResponse.data?.simulateAuthorization?.approved}")

            // Convert the response
            val authorization =
                mutationResponse.data?.simulateAuthorization
                    ?: throw SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildAuthorizationFromMutationResult(authorization)
        } catch (e: Throwable) {
            logger.debug("error $e")
            if (e.isAuthenticationFailure()) {
                throw SudoVirtualCardsSimulatorClient.AuthorizationException.AuthenticationException(e.message)
            }
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.AuthorizationException,
                -> throw e
                else -> throw SudoVirtualCardsSimulatorClient.AuthorizationException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateIncrementalAuthorization(request: SimulateIncrementalAuthorizationInput): SimulateAuthorizationResponse {
        try {
            val mutationInput =
                SimulateIncrementalAuthorizationRequest(
                    authorizationId = request.authorizationId,
                    amount = request.amount,
                )

            val mutationResponse =
                graphQLClient.mutate<
                    SimulateIncrementalAuthorizationMutation,
                    SimulateIncrementalAuthorizationMutation.Data,
                >(
                    SimulateIncrementalAuthorizationMutation.OPERATION_DOCUMENT,
                    mapOf("input" to mutationInput),
                )

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretAuthorizeError(mutationResponse.errors.first())
            }

            val isApproved = mutationResponse.data?.simulateIncrementalAuthorization?.approved
            logger.verbose("approved=$isApproved")

            // Convert the response
            val authorization =
                mutationResponse.data?.simulateIncrementalAuthorization
                    ?: throw SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildAuthorizationFromMutationResult(authorization)
        } catch (e: Throwable) {
            logger.debug("error $e")
            if (e.isAuthenticationFailure()) {
                throw SudoVirtualCardsSimulatorClient.AuthorizationException.AuthenticationException(e.message)
            }
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.AuthorizationException,
                -> throw e
                else -> throw SudoVirtualCardsSimulatorClient.AuthorizationException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateAuthorizationExpiry(authorizationId: String): SimulateAuthorizationExpiryResponse {
        try {
            val mutationInput =
                SimulateAuthorizationExpiryRequest(
                    authorizationId = authorizationId,
                )

            val mutationResponse =
                graphQLClient.mutate<SimulateAuthorizationExpiryMutation, SimulateAuthorizationExpiryMutation.Data>(
                    SimulateAuthorizationExpiryMutation.OPERATION_DOCUMENT,
                    mapOf("input" to mutationInput),
                )

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretAuthorizeError(mutationResponse.errors.first())
            }

            // Convert the response
            val expiry =
                mutationResponse.data?.simulateAuthorizationExpiry
                    ?: throw SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildAuthorizationExpiryFromMutationResult(expiry)
        } catch (e: Throwable) {
            logger.debug("error $e")
            if (e.isAuthenticationFailure()) {
                throw SudoVirtualCardsSimulatorClient.AuthorizationException.AuthenticationException(e.message)
            }
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.AuthorizationException,
                -> throw e
                else -> throw SudoVirtualCardsSimulatorClient.AuthorizationException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateDebit(request: SimulateDebitInput): SimulateDebitResponse {
        try {
            val mutationInput =
                SimulateDebitRequest(
                    authorizationId = request.authorizationId,
                    amount = request.amount,
                )

            val mutationResponse =
                graphQLClient.mutate<SimulateDebitMutation, SimulateDebitMutation.Data>(
                    SimulateDebitMutation.OPERATION_DOCUMENT,
                    mapOf("input" to mutationInput),
                )

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretDebitError(mutationResponse.errors.first())
            }

            logger.verbose("succeeded")

            // Convert the response
            val debit =
                mutationResponse.data?.simulateDebit
                    ?: throw SudoVirtualCardsSimulatorClient.DebitException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildDebitFromMutationResult(debit)
        } catch (e: Throwable) {
            logger.debug("error $e")
            if (e.isAuthenticationFailure()) {
                throw SudoVirtualCardsSimulatorClient.DebitException.AuthenticationException(e.message)
            }
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.DebitException,
                -> throw e
                else -> throw SudoVirtualCardsSimulatorClient.DebitException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateRefund(request: SimulateRefundInput): SimulateRefundResponse {
        try {
            val mutationInput =
                SimulateRefundRequest(
                    debitId = request.debitId,
                    amount = request.amount,
                )

            val mutationResponse =
                graphQLClient.mutate<SimulateRefundMutation, SimulateRefundMutation.Data>(
                    SimulateRefundMutation.OPERATION_DOCUMENT,
                    mapOf("input" to mutationInput),
                )

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretRefundError(mutationResponse.errors.first())
            }

            logger.verbose("succeeded")

            // Convert the response
            val debit =
                mutationResponse.data?.simulateRefund
                    ?: throw SudoVirtualCardsSimulatorClient.RefundException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildRefundFromMutationResult(debit)
        } catch (e: Throwable) {
            logger.debug("error $e")
            if (e.isAuthenticationFailure()) {
                throw SudoVirtualCardsSimulatorClient.RefundException.AuthenticationException(e.message)
            }
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.RefundException,
                -> throw e
                else -> throw SudoVirtualCardsSimulatorClient.RefundException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateReversal(request: SimulateReversalInput): SimulateReversalResponse {
        try {
            val mutationInput =
                SimulateReversalRequest(
                    authorizationId = request.authorizationId,
                    amount = request.amount,
                )

            val mutationResponse =
                graphQLClient.mutate<SimulateReversalMutation, SimulateReversalMutation.Data>(
                    SimulateReversalMutation.OPERATION_DOCUMENT,
                    mapOf("input" to mutationInput),
                )

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors}")
                throw interpretReversalError(mutationResponse.errors.first())
            }

            logger.verbose("succeeded")

            // Convert the response
            val debit =
                mutationResponse.data?.simulateReversal
                    ?: throw SudoVirtualCardsSimulatorClient.ReversalException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildReversalFromMutationResult(debit)
        } catch (e: Throwable) {
            logger.debug("error $e")
            if (e.isAuthenticationFailure()) {
                throw SudoVirtualCardsSimulatorClient.ReversalException.AuthenticationException(e.message)
            }
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.ReversalException,
                -> throw e
                else -> throw SudoVirtualCardsSimulatorClient.ReversalException.UnknownException(cause = e)
            }
        }
    }
}

private fun interpretGetMerchantsError(e: GraphQLResponse.Error): SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException {
    val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
    return if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
        httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN ||
        e.isAuthenticationFailure()
    ) {
        SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.AuthenticationException(e.message)
    } else {
        SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.FailedException(
            e.message,
        )
    }
}

private fun interpretGetConversionRatesError(
    e: GraphQLResponse.Error,
): SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException {
    val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
    return if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
        httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN ||
        e.isAuthenticationFailure()
    ) {
        SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.AuthenticationException(e.message)
    } else {
        SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.FailedException(
            e.message,
        )
    }
}

private fun interpretAuthorizeError(e: GraphQLResponse.Error): SudoVirtualCardsSimulatorClient.AuthorizationException {
    val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
    return if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
        httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN ||
        e.isAuthenticationFailure()
    ) {
        SudoVirtualCardsSimulatorClient.AuthorizationException.AuthenticationException(e.message)
    } else {
        val error = e.extensions?.get(GRAPHQL_ERROR_TYPE)?.toString() ?: ""
        if (error.contains(ERROR_CARD_NOT_FOUND)) {
            return SudoVirtualCardsSimulatorClient.AuthorizationException.CardNotFoundException("Card not found")
        } else if (error.contains(ERROR_TRANSACTION_NOT_FOUND)) {
            return SudoVirtualCardsSimulatorClient.AuthorizationException.AuthorizationNotFoundException(AUTHORIZATION_NOT_FOUND)
        } else if (error.contains(ERROR_ALREADY_EXPIRED)) {
            return SudoVirtualCardsSimulatorClient.AuthorizationException.AuthorizationExpiredException("Authorization already expired")
        }
        SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(e.toString())
    }
}

private fun interpretDebitError(e: GraphQLResponse.Error): SudoVirtualCardsSimulatorClient.DebitException {
    val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
    return if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
        httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN ||
        e.isAuthenticationFailure()
    ) {
        SudoVirtualCardsSimulatorClient.DebitException.AuthenticationException(e.message)
    } else {
        val error =
            e.extensions
                ?.get(
                    GRAPHQL_ERROR_TYPE,
                )?.toString() ?: ""
        if (error.contains(ERROR_TRANSACTION_NOT_FOUND)) {
            return SudoVirtualCardsSimulatorClient.DebitException.AuthorizationNotFoundException(AUTHORIZATION_NOT_FOUND)
        }
        SudoVirtualCardsSimulatorClient.DebitException.FailedException(e.toString())
    }
}

private fun interpretRefundError(e: GraphQLResponse.Error): SudoVirtualCardsSimulatorClient.RefundException {
    val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
    return if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
        httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN ||
        e.isAuthenticationFailure()
    ) {
        SudoVirtualCardsSimulatorClient.RefundException.AuthenticationException(e.message)
    } else {
        val error =
            e.extensions
                ?.get(
                    GRAPHQL_ERROR_TYPE,
                )?.toString() ?: ""
        if (error.contains(ERROR_TRANSACTION_NOT_FOUND)) {
            return SudoVirtualCardsSimulatorClient.RefundException.DebitNotFoundException("Debit not found")
        } else if (error.contains(ERROR_EXCESSIVE_REFUND)) {
            return SudoVirtualCardsSimulatorClient.RefundException.ExcessiveRefundException("Refund amount exceeds debit amount")
        }
        SudoVirtualCardsSimulatorClient.RefundException.FailedException(e.toString())
    }
}

private fun interpretReversalError(e: GraphQLResponse.Error): SudoVirtualCardsSimulatorClient.ReversalException {
    val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
    return if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
        httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN ||
        e.isAuthenticationFailure()
    ) {
        SudoVirtualCardsSimulatorClient.ReversalException.AuthenticationException(e.message)
    } else {
        val error = e.extensions?.get(GRAPHQL_ERROR_TYPE)?.toString() ?: ""
        if (error.contains(ERROR_TRANSACTION_NOT_FOUND)) {
            return SudoVirtualCardsSimulatorClient.ReversalException.AuthorizationNotFoundException(AUTHORIZATION_NOT_FOUND)
        } else if (error.contains(ERROR_EXCESSIVE_REVERSAL)) {
            return SudoVirtualCardsSimulatorClient.ReversalException.ExcessiveReversalException("Reversal amount exceeds debit amount")
        }
        SudoVirtualCardsSimulatorClient.ReversalException.FailedException(e.toString())
    }
}

/** Return the message if this is an authentication error, null otherwise */
private fun GraphQLResponse.Error.isAuthenticationFailure(): Boolean =
    (
        this.message.contains("Cognito User Pools token") ||
            this.message.contains("Cognito Identity") ||
            this.message.contains("Cognito UserPool")
    )

private fun Throwable.isAuthenticationFailure(): Boolean {
    var cause: Throwable?
    val maxDepth = 4
    var depth = 0
    do {
        val msg = this.message
        if (msg != null && (
                msg.contains("Cognito User Pools token") ||
                    msg.contains("Cognito Identity") ||
                    msg.contains("Cognito UserPool")
            )
        ) {
            return true
        }
        cause = this.cause
        ++depth
    } while (cause != null && depth < maxDepth)

    return false
}
