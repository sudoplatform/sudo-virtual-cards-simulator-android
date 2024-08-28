/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

/**
 * A request to create an authorization in the simulator.
 *
 * @property cardNumber [String] Card number (Primary Account Number) of card presented to merchant.
 * @property amount [Int] Amount of transaction in merchant's minor currency, e.g. cents for USD.
 * @property merchantId [String] Identifier of the merchant to use in simulated authorization.
 * @property expirationMonth [Int] Card expiry month entered by user at merchant checkout, 2 digits.
 * @property expirationYear [Int] Card expiry year entered by user at merchant checkout, 4 digits.
 * @property billingAddress [BillingAddress] Billing address entered by user at merchant checkout. If absent, will simulate an address
 *  check as NOT_PROVIDED.
 * @property securityCode [String] Security code from the back of the card entered by the user at merchant checkout, 3 or 4 digits.
 * If absent, will simulate a security code check as NOT_PROVIDED.
 */
data class SimulateAuthorizationInput(
    val cardNumber: String,
    val amount: Int,
    val merchantId: String,
    val expirationMonth: Int,
    val expirationYear: Int,
    val billingAddress: BillingAddress? = null,
    val securityCode: String? = null,
)
