/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import java.util.Date

/**
 * A debit created in the simulator.
 *
 * @property id Identifier of the debit response.
 * @property amount The amount of the debit in the merchant's minor currency e.g. cents for USD.
 * @property currency ISO 4217 currency code in which the merchant charges
 * @property createdAt When this debit was created.
 * @property updatedAt When this debit was most recently updated.
 *
 * @since 2020-07-01
 */
data class SimulateDebitResponse(
    val id: String,
    val amount: Int,
    val currency: String,
    val createdAt: Date = Date(System.currentTimeMillis()),
    val updatedAt: Date = Date(System.currentTimeMillis())
)
