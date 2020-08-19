/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.transformers

import com.sudoplatform.sudovirtualcards.simulator.graphql.ListSimulatorConversionRatesQuery
import com.sudoplatform.sudovirtualcards.simulator.graphql.ListSimulatorMerchantsQuery
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateAuthorizationExpiryMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateAuthorizationMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateDebitMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateIncrementalAuthorizationMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateRefundMutation
import com.sudoplatform.sudovirtualcards.simulator.graphql.SimulateReversalMutation
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateAuthorizationExpiryResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateAuthorizationResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateDebitResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateRefundResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulateReversalResponse
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.CurrencyAmount
import com.sudoplatform.sudovirtualcards.simulator.types.outputs.SimulatorMerchant
import java.util.Date

/**
 * Transform the data types returned by GraphQL operations to the data types that are exposed to users.
 */
internal object SudoVirtualCardsSimulatorTransformer {

    /**
     * Transform the results of the list simulator merchants query
     *
     * @param queryResults The graphql query results
     * @return The list of [SimulatorMerchant]s
     */
    fun buildMerchantsFromQueryResults(queryResults: List<ListSimulatorMerchantsQuery.ListSimulatorMerchant>): List<SimulatorMerchant> {
        return queryResults.map { merchant ->
            SimulatorMerchant(
                id = merchant.id(),
                name = merchant.name(),
                description = merchant.description(),
                mcc = merchant.mcc(),
                city = merchant.city(),
                state = merchant.state().takeIf { it != null },
                postalCode = merchant.postalCode(),
                country = merchant.country(),
                currency = merchant.currency(),
                declineAfterAuthorization = merchant.declineAfterAuthorization(),
                declineBeforeAuthorization = merchant.declineBeforeAuthorization(),
                createdAt = merchant.createdAtEpochMs().toDate(),
                updatedAt = merchant.updatedAtEpochMs().toDate()
            )
        }.toList()
    }

    /**
     * Transform the results of the list conversion rates query into a list of currencies
     *
     * @param queryResults The graphql query results
     * @return The list of [CurrencyAmount]s
     */
    fun buildCurrenciesFromQueryResults(
        queryResults: List<ListSimulatorConversionRatesQuery.ListSimulatorConversionRate>
    ): List<CurrencyAmount> {
        return queryResults.map { conversionRate ->
            CurrencyAmount(
                currency = conversionRate.currency(),
                amount = conversionRate.amount()
            )
        }.toList()
    }

    /**
     * Transform the results of the simulate authorization mutation to a response
     *
     * @param mutationResult The graphql mutation result
     * @return The [SimulateAuthorizationResponse]
     */
    fun buildAuthorizationFromMutationResult(
        mutationResult: SimulateAuthorizationMutation.SimulateAuthorization
    ): SimulateAuthorizationResponse {
        return SimulateAuthorizationResponse(
            id = mutationResult.id(),
            isApproved = mutationResult.approved(),
            amount = mutationResult.billedAmount()?.amount(),
            currency = mutationResult.billedAmount()?.currency(),
            declineReason = mutationResult.declineReason(),
            createdAt = mutationResult.createdAtEpochMs().toDate(),
            updatedAt = mutationResult.updatedAtEpochMs().toDate()
        )
    }

    /**
     * Transform the results of the simulate incremental authorization mutation to a response
     *
     * @param mutationResult The graphql mutation result
     * @return The [SimulateAuthorizationResponse]
     */
    fun buildAuthorizationFromMutationResult(
        mutationResult: SimulateIncrementalAuthorizationMutation.SimulateIncrementalAuthorization
    ): SimulateAuthorizationResponse {
        return SimulateAuthorizationResponse(
            id = mutationResult.id(),
            isApproved = mutationResult.approved(),
            amount = mutationResult.billedAmount()?.amount(),
            currency = mutationResult.billedAmount()?.currency(),
            declineReason = mutationResult.declineReason(),
            createdAt = mutationResult.createdAtEpochMs().toDate(),
            updatedAt = mutationResult.updatedAtEpochMs().toDate()
        )
    }

    /**
     * Transform the results of the simulate authorization expiry mutation to a response
     *
     * @param mutationResult The graphql mutation result
     * @return The [SimulateAuthorizationExpiryResponse]
     */
    fun buildAuthorizationExpiryFromMutationResult(
        mutationResult: SimulateAuthorizationExpiryMutation.SimulateAuthorizationExpiry
    ): SimulateAuthorizationExpiryResponse {
        return SimulateAuthorizationExpiryResponse(
            id = mutationResult.id(),
            createdAt = mutationResult.createdAtEpochMs().toDate(),
            updatedAt = mutationResult.updatedAtEpochMs().toDate()
        )
    }

    /**
     * Transform the results of the simulate debit mutation to a response
     *
     * @param mutationResult The graphql mutation result
     * @return The [SimulateDebitResponse]
     */
    fun buildDebitFromMutationResult(
        mutationResult: SimulateDebitMutation.SimulateDebit
    ): SimulateDebitResponse {
        return SimulateDebitResponse(
            id = mutationResult.id(),
            amount = mutationResult.billedAmount().amount(),
            currency = mutationResult.billedAmount().currency(),
            createdAt = mutationResult.createdAtEpochMs().toDate(),
            updatedAt = mutationResult.updatedAtEpochMs().toDate()
        )
    }

    /**
     * Transform the results of the simulate refund mutation to a response
     *
     * @param mutationResult The graphql mutation result
     * @return The [SimulateRefundResponse]
     */
    fun buildRefundFromMutationResult(
        mutationResult: SimulateRefundMutation.SimulateRefund
    ): SimulateRefundResponse {
        return SimulateRefundResponse(
            id = mutationResult.id(),
            amount = mutationResult.billedAmount().amount(),
            currency = mutationResult.billedAmount().currency(),
            createdAt = mutationResult.createdAtEpochMs().toDate(),
            updatedAt = mutationResult.updatedAtEpochMs().toDate()
        )
    }

    /**
     * Transform the results of the simulate reversal mutation to a response
     *
     * @param mutationResult The graphql mutation result
     * @return The [SimulateReversalResponse]
     */
    fun buildReversalFromMutationResult(
        mutationResult: SimulateReversalMutation.SimulateReversal
    ): SimulateReversalResponse {
        return SimulateReversalResponse(
            id = mutationResult.id(),
            amount = mutationResult.billedAmount().amount(),
            currency = mutationResult.billedAmount().currency(),
            createdAt = mutationResult.createdAtEpochMs().toDate(),
            updatedAt = mutationResult.updatedAtEpochMs().toDate()
        )
    }
}

private fun Double.toDate(): Date {
    return Date(this.toLong())
}
