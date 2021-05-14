/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
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
 * @property id Identifier of the merchant for use in simulated transaction requests
 * @property name Name of the merchant, which is used in transaction descriptions
 * @property description Description of the merchant
 * @property mcc Merchant category code according to ISO 18245
 * @property city City of the merchant
 * @property state State or province of the merchant
 * @property postalCode Postal code of the merchant
 * @property country Country of the merchant according to ISO-3166 Alpha-2.
 * @property currency ISO 4217 currency code in which the merchant charges
 * @property declineAfterAuthorization If true a transaction request made to this merchant will be authorized at the
 * virtual cards service level, and then immediately declined once it reaches the provider level
 * @property declineBeforeAuthorization If true a transaction request made to this merchant will be automatically
 * declined before it reaches the authorization level at the Virtual Card Service
 * @property createdAt When the merchant was created
 * @property updatedAt When the merchant was last updated
 *
 * @since 2020-05-22
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
