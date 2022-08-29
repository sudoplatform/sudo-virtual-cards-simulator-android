/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * A simulated virtual card merchant.
 *
 * @property id [String] Identifier of the merchant for use in simulated transaction requests
 * @property name [String] Name of the merchant, which is used in transaction descriptions
 * @property description [String] Description of the merchant
 * @property mcc [String] Merchant category code according to ISO 18245
 * @property city [String] City of the merchant
 * @property state [String] State or province of the merchant
 * @property postalCode [String] Postal code of the merchant
 * @property country [String] Country of the merchant according to ISO-3166 Alpha-2.
 * @property currency [String] ISO 4217 currency code in which the merchant charges
 * @property declineAfterAuthorization [Boolean] If true a transaction request made to this merchant will be authorized at the
 * virtual cards service level, and then immediately declined once it reaches the provider level
 * @property declineBeforeAuthorization [Boolean] If true a transaction request made to this merchant will be automatically
 * declined before it reaches the authorization level at the Virtual Card Service
 * @property createdAt [Date] When the merchant was created
 * @property updatedAt [Date] When the merchant was last updated
 */
@Parcelize
data class SimulatorMerchant(
    val id: String,
    val name: String,
    val description: String,
    val mcc: String,
    val city: String,
    val state: String? = null,
    val postalCode: String,
    val country: String,
    val currency: String,
    val declineAfterAuthorization: Boolean,
    val declineBeforeAuthorization: Boolean,
    val createdAt: Date,
    val updatedAt: Date
) : Parcelable
