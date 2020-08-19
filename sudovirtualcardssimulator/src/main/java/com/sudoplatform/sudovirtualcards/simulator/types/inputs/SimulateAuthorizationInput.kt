/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

/**
 * A request to create an authorization in the simulator.
 *
 * @property cardNumber Card number (Primary Account Number) of card presented to merchant
 * @property amount Amount of transaction in merchant's minor currency, e.g. cents for USD
 * @property merchantId Identifier of the merchant to use in simulated authorization
 * @property expirationMonth Card expiry month entered by user at merchant checkout, 2 digits.
 * @property expirationYear Card expiry year entered by user at merchant checkout, 4 digits.
 * @property billingAddress Billing address entered by user at merchant checkout. If absent, will simulate an address check as NOT_PROVIDED
 * @property securityCode Security code from the back of the card entered by the user at merchant checkout, 3 or 4 digits.
 * If absent, will simulate a security code check as NOT_PROVIDED.
 *
 * @since 2020-06-09
 */
data class SimulateAuthorizationInput(
    val cardNumber: String,
    val amount: Int,
    val merchantId: String,
    val expirationMonth: Int,
    val expirationYear: Int,
    val billingAddress: BillingAddress? = null,
    val securityCode: String? = null
) {
    constructor(
        cardNumber: String,
        amount: Int,
        merchantId: String,
        expirationMonth: Int,
        expirationYear: Int,
        addressLine1: String,
        addressLine2: String? = null,
        city: String,
        state: String,
        postalCode: String,
        country: String,
        securityCode: String? = null
    ) : this(
        cardNumber = cardNumber,
        amount = amount,
        merchantId = merchantId,
        expirationMonth = expirationMonth,
        expirationYear = expirationYear,
        billingAddress = BillingAddress(addressLine1, addressLine2, city, state, postalCode, country),
        securityCode = securityCode
    )
}
