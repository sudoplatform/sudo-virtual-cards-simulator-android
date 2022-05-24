/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudovirtualcards.simulator.logging.LogConstants
import com.sudoplatform.sudovirtualcards.simulator.appsync.enqueue
import com.sudoplatform.sudovirtualcards.simulator.appsync.enqueueFirst
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
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateAuthorizationInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateDebitInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateIncrementalAuthorizationInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateRefundInput
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.SimulateReversalInput
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateAuthorizationExpiryResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateAuthorizationResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateDebitResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateRefundResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateReversalResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.CurrencyAmount
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulatorMerchant
import com.sudoplatform.sudovirtualcards.simulator.types.transformers.SudoVirtualCardsSimulatorTransformer
import kotlinx.coroutines.CancellationException

// Exception messages
private const val NO_SERVER_RESPONSE = "No response from server"
private const val AUTHORIZATION_NOT_FOUND = "Authorization not found"

// Errors returned from the backend
private const val ERROR_TYPE = "errorType"
private const val ERROR_TRANSACTION_NOT_FOUND = "TransactionNotFoundError"
private const val ERROR_CARD_NOT_FOUND = "CardNotFoundError"
private const val ERROR_EXCESSIVE_REVERSAL = "ExcessiveReversalError"
private const val ERROR_EXCESSIVE_REFUND = "ExcessiveRefundError"
private const val ERROR_ALREADY_EXPIRED = "AlreadyExpiredError"

/**
 * Default implementation of the [SudoVirtualCardsSimulatorClient] interface.
 *
 * @property appSyncClient GraphQL client used to make requests to AWS and call sudo virtual cards service API.
 * @property logger Errors and warnings will be logged here
 */
internal class DefaultSudoVirtualCardsSimulatorClient(
    private val appSyncClient: AWSAppSyncClient,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))
) : SudoVirtualCardsSimulatorClient {

    /**
     * Checksum's for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code. We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry. The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "3.0.1"

    override suspend fun getSimulatorMerchants(): List<SimulatorMerchant> {
        try {
            val query = ListSimulatorMerchantsQuery.builder().build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.CACHE_AND_NETWORK)
                .enqueueFirst()

            // Check if there was an error within the server
            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.FailedException(
                    queryResponse.errors().first().message()
                )
            }

            logger.verbose("${queryResponse.data()?.listSimulatorMerchants()?.size ?: 0} merchants returned")

            // Iterate over results and build merchants
            val results = queryResponse.data()?.listSimulatorMerchants() ?: emptyList()
            return SudoVirtualCardsSimulatorTransformer.buildMerchantsFromQueryResults(results)
        } catch (e: Throwable) {
            logger.debug("error $e")
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException -> throw e
                is ApolloException -> throw interpretGetMerchantsFailure(e)
                else -> throw SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun getSimulatorConversionRates(): List<CurrencyAmount> {
        try {
            val query = ListSimulatorConversionRatesQuery.builder().build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.CACHE_AND_NETWORK)
                .enqueueFirst()

            val rateCount = queryResponse.data()?.listSimulatorConversionRates()?.size ?: 0
            logger.verbose("$rateCount rates returned")

            // Check if there was an error within the server
            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.FailedException(
                    queryResponse.errors().first().message()
                )
            }

            // Iterate over results and build merchants
            val results = queryResponse.data()?.listSimulatorConversionRates() ?: emptyList()
            return SudoVirtualCardsSimulatorTransformer.buildCurrenciesFromQueryResults(results)
        } catch (e: Throwable) {
            logger.debug("error $e")
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException -> throw e
                is ApolloException -> throw interpretGetConversionRatesFailure(e)
                else -> throw SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateAuthorization(request: SimulateAuthorizationInput): SimulateAuthorizationResponse {
        try {
            val expiry = ExpiryInput.builder()
                .mm(request.expirationMonth)
                .yyyy(request.expirationYear)
                .build()

            val mutationInput = SimulateAuthorizationRequest.builder()
                .amount(request.amount)
                .pan(request.cardNumber)
                .merchantId(request.merchantId)
                .csc(request.securityCode)
                .expiry(expiry)

            if (request.billingAddress != null) {
                with(request.billingAddress) {
                    val address = EnteredAddressInput.builder()
                        .addressLine1(addressLine1)
                        .addressLine2(addressLine2)
                        .city(city)
                        .state(state)
                        .country(country)
                        .postalCode(postalCode)
                        .build()
                    mutationInput.billingAddress(address)
                }
            }

            val mutation = SimulateAuthorizationMutation.builder()
                .input(mutationInput.build())
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretAuthorizeError(mutationResponse.errors().first())
            }

            logger.verbose("approved=${mutationResponse.data()?.simulateAuthorization()?.approved()}")

            // Convert the response
            val authorization = mutationResponse.data()?.simulateAuthorization()
                ?: throw SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildAuthorizationFromMutationResult(authorization)
        } catch (e: Throwable) {
            logger.debug("error $e")
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.AuthorizationException -> throw e
                is ApolloException -> throw interpretAuthorizeFailure(e)
                else -> throw SudoVirtualCardsSimulatorClient.AuthorizationException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateIncrementalAuthorization(request: SimulateIncrementalAuthorizationInput): SimulateAuthorizationResponse {
        try {
            val mutationInput = SimulateIncrementalAuthorizationRequest.builder()
                .authorizationId(request.authorizationId)
                .amount(request.amount)
                .build()

            val mutation = SimulateIncrementalAuthorizationMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretAuthorizeError(mutationResponse.errors().first())
            }

            val isApproved = mutationResponse.data()?.simulateIncrementalAuthorization()?.approved()
            logger.verbose("approved=$isApproved")

            // Convert the response
            val authorization = mutationResponse.data()?.simulateIncrementalAuthorization()
                ?: throw SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildAuthorizationFromMutationResult(authorization)
        } catch (e: Throwable) {
            logger.debug("error $e")
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.AuthorizationException -> throw e
                is ApolloException -> throw interpretAuthorizeFailure(e)
                else -> throw SudoVirtualCardsSimulatorClient.AuthorizationException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateAuthorizationExpiry(authorizationId: String): SimulateAuthorizationExpiryResponse {
        try {
            val mutationInput = SimulateAuthorizationExpiryRequest.builder()
                .authorizationId(authorizationId)
                .build()

            val mutation = SimulateAuthorizationExpiryMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretAuthorizeError(mutationResponse.errors().first())
            }

            // Convert the response
            val expiry = mutationResponse.data()?.simulateAuthorizationExpiry()
                ?: throw SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildAuthorizationExpiryFromMutationResult(expiry)
        } catch (e: Throwable) {
            logger.debug("error $e")
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.AuthorizationException -> throw e
                is ApolloException -> throw interpretAuthorizeFailure(e)
                else -> throw SudoVirtualCardsSimulatorClient.AuthorizationException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateDebit(request: SimulateDebitInput): SimulateDebitResponse {
        try {
            val mutationInput = SimulateDebitRequest.builder()
                .authorizationId(request.authorizationId)
                .amount(request.amount)
                .build()

            val mutation = SimulateDebitMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretDebitError(mutationResponse.errors().first())
            }

            logger.verbose("succeeded")

            // Convert the response
            val debit = mutationResponse.data()?.simulateDebit()
                ?: throw SudoVirtualCardsSimulatorClient.DebitException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildDebitFromMutationResult(debit)
        } catch (e: Throwable) {
            logger.debug("error $e")
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.DebitException -> throw e
                is ApolloException -> throw interpretDebitFailure(e)
                else -> throw SudoVirtualCardsSimulatorClient.DebitException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateRefund(request: SimulateRefundInput): SimulateRefundResponse {
        try {
            val mutationInput = SimulateRefundRequest.builder()
                .debitId(request.debitId)
                .amount(request.amount)
                .build()

            val mutation = SimulateRefundMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretRefundError(mutationResponse.errors().first())
            }

            logger.verbose("succeeded")

            // Convert the response
            val debit = mutationResponse.data()?.simulateRefund()
                ?: throw SudoVirtualCardsSimulatorClient.RefundException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildRefundFromMutationResult(debit)
        } catch (e: Throwable) {
            logger.debug("error $e")
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.RefundException -> throw e
                is ApolloException -> throw interpretRefundFailure(e)
                else -> throw SudoVirtualCardsSimulatorClient.RefundException.UnknownException(cause = e)
            }
        }
    }

    override suspend fun simulateReversal(request: SimulateReversalInput): SimulateReversalResponse {
        try {
            val mutationInput = SimulateReversalRequest.builder()
                .authorizationId(request.authorizationId)
                .amount(request.amount)
                .build()

            val mutation = SimulateReversalMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            // Check if there was an error within the server
            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretReversalError(mutationResponse.errors().first())
            }

            logger.verbose("succeeded")

            // Convert the response
            val debit = mutationResponse.data()?.simulateReversal()
                ?: throw SudoVirtualCardsSimulatorClient.ReversalException.FailedException(NO_SERVER_RESPONSE)
            return SudoVirtualCardsSimulatorTransformer.buildReversalFromMutationResult(debit)
        } catch (e: Throwable) {
            logger.debug("error $e")
            when (e) {
                is CancellationException,
                is SudoVirtualCardsSimulatorClient.ReversalException -> throw e
                is ApolloException -> throw interpretReversalFailure(e)
                else -> throw SudoVirtualCardsSimulatorClient.ReversalException.UnknownException(cause = e)
            }
        }
    }
}

private fun interpretGetMerchantsFailure(e: ApolloException): SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException {
    return e.isAuthenticationFailure()?.let {
        SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.AuthenticationException(it.first, it.second)
    }
        ?: SudoVirtualCardsSimulatorClient.GetSimulatorMerchantsException.FailedException(cause = e)
}

private fun interpretGetConversionRatesFailure(e: ApolloException): SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException {
    return e.isAuthenticationFailure()?.let {
        SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.AuthenticationException(it.first, it.second)
    }
        ?: SudoVirtualCardsSimulatorClient.GetSimulatorConversionRatesException.FailedException(cause = e)
}

private fun interpretAuthorizeFailure(e: ApolloException): SudoVirtualCardsSimulatorClient.AuthorizationException {
    return e.isAuthenticationFailure()?.let {
        SudoVirtualCardsSimulatorClient.AuthorizationException.AuthenticationException(it.first, it.second)
    }
        ?: SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(cause = e)
}

private fun interpretAuthorizeError(e: Error): SudoVirtualCardsSimulatorClient.AuthorizationException {
    val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
    if (error.contains(ERROR_CARD_NOT_FOUND)) {
        return SudoVirtualCardsSimulatorClient.AuthorizationException.CardNotFoundException("Card not found")
    } else if (error.contains(ERROR_TRANSACTION_NOT_FOUND)) {
        return SudoVirtualCardsSimulatorClient.AuthorizationException.AuthorizationNotFoundException(AUTHORIZATION_NOT_FOUND)
    } else if (error.contains(ERROR_ALREADY_EXPIRED)) {
        return SudoVirtualCardsSimulatorClient.AuthorizationException.AuthorizationExpiredException("Authorization already expired")
    }
    return SudoVirtualCardsSimulatorClient.AuthorizationException.FailedException(e.toString())
}

private fun interpretDebitError(e: Error): SudoVirtualCardsSimulatorClient.DebitException {
    val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
    if (error.contains(ERROR_TRANSACTION_NOT_FOUND)) {
        return SudoVirtualCardsSimulatorClient.DebitException.AuthorizationNotFoundException(AUTHORIZATION_NOT_FOUND)
    }
    return SudoVirtualCardsSimulatorClient.DebitException.FailedException(e.toString())
}

private fun interpretDebitFailure(e: ApolloException): SudoVirtualCardsSimulatorClient.DebitException {
    return e.isAuthenticationFailure()?.let {
        SudoVirtualCardsSimulatorClient.DebitException.AuthenticationException(it.first, it.second)
    }
        ?: SudoVirtualCardsSimulatorClient.DebitException.FailedException(cause = e)
}

private fun interpretRefundError(e: Error): SudoVirtualCardsSimulatorClient.RefundException {
    val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
    if (error.contains(ERROR_TRANSACTION_NOT_FOUND)) {
        return SudoVirtualCardsSimulatorClient.RefundException.DebitNotFoundException("Debit not found")
    } else if (error.contains(ERROR_EXCESSIVE_REFUND)) {
        return SudoVirtualCardsSimulatorClient.RefundException.ExcessiveRefundException("Refund amount exceeds debit amount")
    }
    return SudoVirtualCardsSimulatorClient.RefundException.FailedException(e.toString())
}

private fun interpretRefundFailure(e: ApolloException): SudoVirtualCardsSimulatorClient.RefundException {
    return e.isAuthenticationFailure()?.let {
        SudoVirtualCardsSimulatorClient.RefundException.AuthenticationException(it.first, it.second)
    }
        ?: SudoVirtualCardsSimulatorClient.RefundException.FailedException(cause = e)
}

private fun interpretReversalError(e: Error): SudoVirtualCardsSimulatorClient.ReversalException {
    val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
    if (error.contains(ERROR_TRANSACTION_NOT_FOUND)) {
        return SudoVirtualCardsSimulatorClient.ReversalException.AuthorizationNotFoundException(AUTHORIZATION_NOT_FOUND)
    } else if (error.contains(ERROR_EXCESSIVE_REVERSAL)) {
        return SudoVirtualCardsSimulatorClient.ReversalException.ExcessiveReversalException("Reversal amount exceeds debit amount")
    }
    return SudoVirtualCardsSimulatorClient.ReversalException.FailedException(e.toString())
}

private fun interpretReversalFailure(e: ApolloException): SudoVirtualCardsSimulatorClient.ReversalException {
    return e.isAuthenticationFailure()?.let {
        SudoVirtualCardsSimulatorClient.ReversalException.AuthenticationException(it.first, it.second)
    }
        ?: SudoVirtualCardsSimulatorClient.ReversalException.FailedException(cause = e)
}

/** Return the message and cause if this is an authentication error, null otherwise */
private fun ApolloException.isAuthenticationFailure(): Pair<String, Throwable>? {

    var cause = this.cause
    while (cause != null) {
        val msg = cause.message
        if (msg != null && (
            msg.contains("Cognito User Pools token") ||
                msg.contains("Cognito Identity") ||
                msg.contains("Cognito UserPool")
            )
        ) {
            return Pair(msg, cause)
        }
        cause = cause.cause
    }

    return null
}
