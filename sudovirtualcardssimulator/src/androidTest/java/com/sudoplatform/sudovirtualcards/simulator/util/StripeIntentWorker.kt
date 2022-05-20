/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.util

import android.content.Context
import com.stripe.android.Stripe
import com.stripe.android.confirmSetupIntent
import com.stripe.android.core.exception.StripeException
import com.stripe.android.model.Address
import com.stripe.android.model.ConfirmSetupIntentParams
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import com.sudoplatform.sudovirtualcards.SudoVirtualCardsClient
import com.sudoplatform.sudovirtualcards.types.ProviderCompletionData
import com.sudoplatform.sudovirtualcards.types.inputs.CreditCardFundingSourceInput
import com.sudoplatform.sudovirtualcards.util.LocaleUtil

/**
 * Test utility worker encapsulating the functionality to perform processing of payment setup
 * confirmation.
 */
internal class StripeIntentWorker(
    private val context: Context,
    private val stripeClient: Stripe
) {
    /**
     * Processes the payment setup confirmation to return the data needed to complete
     * the funding source creation process.
     *
     * @param input The credit card input required to build the card and billing details.
     * @param clientSecret The client secret from the provisional funding source provisioning data.
     */
    suspend fun confirmSetupIntent(
        input: CreditCardFundingSourceInput,
        clientSecret: String,
    ): ProviderCompletionData {
        // Build card details
        val cardDetails = PaymentMethodCreateParams.Card.Builder()
            .setNumber(input.cardNumber)
            .setExpiryMonth(input.expirationMonth)
            .setExpiryYear(input.expirationYear)
            .setCvc(input.securityCode)
            .build()
        // Build billing details
        val billingDetails = PaymentMethod.BillingDetails.Builder()
            .setAddress(
                Address.Builder()
                    .setLine1(input.addressLine1)
                    .setLine2(input.addressLine2)
                    .setCity(input.city)
                    .setState(input.state)
                    .setPostalCode(input.postalCode)
                    .setCountry(ensureAlpha2CountryCode(context, input.country))
                    .build()
            )
            .build()
        // Confirm setup
        val cardParams = PaymentMethodCreateParams.create(cardDetails, billingDetails)
        val confirmParams = ConfirmSetupIntentParams.create(cardParams, clientSecret)
        val setupIntent =
            try {
                stripeClient.confirmSetupIntent(confirmParams)
            } catch (e: StripeException) {
                throw SudoVirtualCardsClient.FundingSourceException.FailedException(e.message)
            }
        // Return completion data
        setupIntent.paymentMethodId?.let {
            return ProviderCompletionData(paymentMethod = it)
        }
        throw SudoVirtualCardsClient.FundingSourceException.FailedException()
    }

    /**
     * Parses the [countryCode] and ensures that it is of a ISO-3166 Alpha-2 format.
     *
     * @param countryCode The country code to parse.
     */
    private fun ensureAlpha2CountryCode(context: Context, countryCode: String): String {
        if (countryCode.trim().length != 3) {
            return countryCode.trim()
        }
        return LocaleUtil.toCountryCodeAlpha2(context, countryCode)
            ?: countryCode
    }
}
