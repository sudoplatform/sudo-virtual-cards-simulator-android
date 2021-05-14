/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * The legal residence of a cardholder for purposes of billing.
 *
 * @property addressLine1 Street address for the cardholder's legal residence.
 * @property addressLine2 Optional secondary address information for the cardholder's legal residence.
 * @property city City of the cardholder's legal residence.
 * @property state State or province of the cardholder's legal residence.
 * @property postalCode Postal code for the cardholder's legal residence.
 * @property country ISO-3166 Alpha-2 country code of the cardholder's legal residence.
 *
 * @since 2020-06-10
 */
@Parcelize
data class BillingAddress(
    val addressLine1: String,
    val addressLine2: String? = null,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
) : Parcelable
