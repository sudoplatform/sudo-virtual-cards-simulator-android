/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * A currency available in the virtual card simulator. The conversion rates are expressed as [Int]
 * that are the relative values between all the currencies. To convert one currency to another find
 * the conversion rates of the two currencies, convert to [Double], divide them then use that relative
 * value to perform the currency conversion.
 *
 * @property currency The ISO 4217 currency code
 * @property amount The amount of a single unit of currency relative to the other currencies in the simulator
 * @sample com.sudoplatform.sudovirtualcards.simulator.samples.Samples.currencyAmount
 * @since 2020-06-05
 */
@Parcelize
data class CurrencyAmount(
    val currency: String,
    val amount: Int
) : Parcelable
