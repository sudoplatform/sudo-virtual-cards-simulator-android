/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudovirtualcards.simulator.logging.LogConstants
import com.sudoplatform.sudovirtualcards.simulator.appsync.AWSAppSyncClientFactory
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.CurrencyAmount
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulatorMerchant
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

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Virtual Cards Simulator service.
 *
 * This interface allows you to simulate the use of a virtual card at merchants to generate transaction events.
 * The simulator is only available in sandbox environments.
 *
 * @sample com.sudoplatform.sudovirtualcards.simulator.samples.Samples.sudoVirtualCardsSimulatorClient
 */
interface SudoVirtualCardsSimulatorClient {

    companion object {
        /** Create a [Builder] for [SudoVirtualCardsSimulatorClient]. */
        @JvmStatic
        fun builder() = Builder()
    }

    /**
     * Builder used to construct the [SudoVirtualCardsSimulatorClient].
     */
    class Builder internal constructor() {
        private var context: Context? = null
        private var username: String? = null
        private var password: String? = null
        private var apiKey: String? = null
        private var appSyncClient: AWSAppSyncClient? = null
        private var logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

        /**
         * Provide the application context (required input).
         */
        fun setContext(context: Context) = also {
            it.context = context
        }

        /**
         * Provide the username to authenticate with the simulator.
         * This is required input if you are not supplying your own [AWSAppSyncClient].
         */
        fun setUsername(username: String) = also {
            it.username = username
        }

        /**
         * Provide the password to authenticate with the simulator.
         * This is required input if you are not supplying your own [AWSAppSyncClient].
         */
        fun setPassword(password: String) = also {
            it.password = password
        }

        /**
         * Provide the API key to authenticate with the simulator.
         * This is required input if you are not supplying your own [AWSAppSyncClient].
         */
        fun setApiKey(apiKey: String) = also {
            it.apiKey = apiKey
        }

        /**
         * Provide an [AWSAppSyncClient] for the [SudoVirtualCardsSimulatorClient] to use
         * (optional input). If you do not supply this value an [AWSAppSyncClient] will
         * be constructed and used. If you provide the [AWSAppSyncClient] via this method
         * you do not need to provide the [context], [username] and [password].
         */
        fun setAppSyncClient(client: AWSAppSyncClient) = also {
            it.appSyncClient = client
        }

        /**
         * Provide the implementation of the [Logger] used for logging errors (optional input).
         * If a value is not supplied a default implementation will be used.
         */
        fun setLogger(logger: Logger) = also {
            this.logger = logger
        }

        /**
         * Construct the [SudoVirtualCardsSimulatorClient]. Will throw a [NullPointerException] if
         * the [context], [username] and [password] or [apiKey] are needed and have not been provided.
         * Will also throw [NullPointerException] if the configuration file does not supply the adminApiService
         * section with the elements apiUrl, poolId, region and clientId.
         */
        fun build(): SudoVirtualCardsSimulatorClient {
            if (appSyncClient == null) {
                // User has not supplied their own AWSAppSyncClient so create our own
                appSyncClient = AWSAppSyncClientFactory.getAppSyncClient(context, username, password, apiKey)
            }
            return DefaultSudoVirtualCardsSimulatorClient(appSyncClient!!, logger)
        }
    }

    /** Exceptions thrown by [getSimulatorMerchants] */
    sealed class GetSimulatorMerchantsException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            GetSimulatorMerchantsException(message = message, cause = cause)
        class FailedException(message: String? = null, cause: Throwable? = null) :
            GetSimulatorMerchantsException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            GetSimulatorMerchantsException(cause = cause)
    }

    /** Exceptions thrown by [getSimulatorConversionRates] */
    sealed class GetSimulatorConversionRatesException(
        message: String? = null,
        cause: Throwable? = null
    ) : RuntimeException(message, cause) {
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            GetSimulatorConversionRatesException(message = message, cause = cause)
        class FailedException(message: String? = null, cause: Throwable? = null) :
            GetSimulatorConversionRatesException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            GetSimulatorConversionRatesException(cause = cause)
    }

    /** Exceptions thrown by [simulateAuthorization] and [simulateIncrementalAuthorization] */
    sealed class AuthorizationException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            AuthorizationException(message = message, cause = cause)
        class CardNotFoundException(message: String? = null, cause: Throwable? = null) :
            AuthorizationException(message = message, cause = cause)
        class AuthorizationNotFoundException(message: String? = null, cause: Throwable? = null) :
            AuthorizationException(message = message, cause = cause)
        class AuthorizationExpiredException(message: String? = null, cause: Throwable? = null) :
            AuthorizationException(message = message, cause = cause)
        class FailedException(message: String? = null, cause: Throwable? = null) :
            AuthorizationException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            AuthorizationException(cause = cause)
    }

    /** Exceptions thrown by [simulateDebit] */
    sealed class DebitException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            DebitException(message = message, cause = cause)
        class AuthorizationNotFoundException(message: String? = null, cause: Throwable? = null) :
            DebitException(message = message, cause = cause)
        class FailedException(message: String? = null, cause: Throwable? = null) :
            DebitException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            DebitException(cause = cause)
    }

    /** Exceptions thrown by [simulateRefund] */
    sealed class RefundException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            RefundException(message = message, cause = cause)
        class DebitNotFoundException(message: String? = null, cause: Throwable? = null) :
            RefundException(message = message, cause = cause)
        class ExcessiveRefundException(message: String? = null, cause: Throwable? = null) :
            RefundException(message = message, cause = cause)
        class FailedException(message: String? = null, cause: Throwable? = null) :
            RefundException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            RefundException(cause = cause)
    }

    /** Exceptions thrown by [simulateReversal] */
    sealed class ReversalException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            ReversalException(message = message, cause = cause)
        class AuthorizationNotFoundException(message: String? = null, cause: Throwable? = null) :
            ReversalException(message = message, cause = cause)
        class ExcessiveReversalException(message: String? = null, cause: Throwable? = null) :
            ReversalException(message = message, cause = cause)
        class FailedException(message: String? = null, cause: Throwable? = null) :
            ReversalException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            ReversalException(cause = cause)
    }

    /**
     * Get the [SimulatorMerchant]s defined in the simulator. This method returns all the supported merchants available to perform
     * transaction simulations.
     *
     * @return [List] of [SimulatorMerchant]s defined in the simulator
     */
    @Throws(GetSimulatorMerchantsException::class)
    suspend fun getSimulatorMerchants(): List<SimulatorMerchant>

    /**
     * Get the list of conversion rates of supported currencies. This method returns all the supported currency conversion
     * rates used by the simulator.
     *
     * @return [List] of [CurrencyAmount]s defined in the simulator
     */
    @Throws(GetSimulatorConversionRatesException::class)
    suspend fun getSimulatorConversionRates(): List<CurrencyAmount>

    /**
     * Simulate an authorization transaction. This causes a pending transaction to appear on the card with the supplied identifier.
     *
     * @param request The transaction data needed to create the authorization
     * @return The authorization or declination of the transaction
     */
    @Throws(AuthorizationException::class)
    suspend fun simulateAuthorization(request: SimulateAuthorizationInput): SimulateAuthorizationResponse

    /**
     * Simulate an incremental authorization transaction. This will increment an authorization transaction to increase its amount.
     *
     * @param request The transaction data needed to create the incremental authorization
     * @return The authorization or declination of the transaction
     */
    @Throws(AuthorizationException::class)
    suspend fun simulateIncrementalAuthorization(request: SimulateIncrementalAuthorizationInput): SimulateAuthorizationResponse

    /**
     * Simulate the expiry of an authorization. This will cause an authorization to expire.
     *
     * @param authorizationId The identifier of the pre-existing authorization that should expire
     * @return The authorization expiry response
     */
    @Throws(AuthorizationException::class)
    suspend fun simulateAuthorizationExpiry(authorizationId: String): SimulateAuthorizationExpiryResponse

    /**
     * Simulate a debit transaction.
     *
     * Simulating a debit will generate a transaction. Simulating a debit does not modify any existing records, and instead generates a
     * new debit transaction
     *
     * Debits can only be performed against a transaction that has already been previously authorized. Authorizations can also be partially
     * debited, which will generate a debit record of the partially debited amount.
     *
     * Debits **can** exceed the total amount of a authorization.
     *
     * @param request The data needed to create the debit
     * @return The details of the debit
     */
    @Throws(DebitException::class)
    suspend fun simulateDebit(request: SimulateDebitInput): SimulateDebitResponse

    /**
     * Simulate a refund transaction.
     *
     * Simulating a refund will generate a refund transaction. Simulating a refund does not modify any existing records, and instead
     * generates a new refund transaction.
     *
     * Refunds can only be performed against a transaction that has already been previously debited. Debits can also be partially refunded,
     * which will generate a new refund transaction record of the partially refunded amount.
     *
     * Refunds cannot exceed the total amount of a debit, otherwise an error will be returned and no operation will be performed.
     *
     * @param request The data needed to create the refund
     * @return The details of the refund
     */
    @Throws(RefundException::class)
    suspend fun simulateRefund(request: SimulateRefundInput): SimulateRefundResponse

    /**
     * Simulate an authorization reversal transaction.
     *
     * Reversals do not generate a transaction the user can see, but will instead reverse an existing authorization in the  pending
     * transaction. A reversal can be partial, which will cause the authorization transaction to be decremented by the input amount,
     * or can be reversed the entire amount. If the entire amount is reversed, the authorization transaction record will be deleted.
     *
     * Reversals cannot exceed the total of the authorization, otherwise an error will be returned and no operation will be performed.
     *
     * @param request The data needed to create the reversal
     * @return The details of the reversal
     */
    @Throws(ReversalException::class)
    suspend fun simulateReversal(request: SimulateReversalInput): SimulateReversalResponse
}
