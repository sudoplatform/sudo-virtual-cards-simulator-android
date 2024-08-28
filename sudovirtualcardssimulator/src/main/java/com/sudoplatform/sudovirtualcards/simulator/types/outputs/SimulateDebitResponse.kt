/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import java.util.Date

/**
 * A debit created in the simulator.
 *
 * @property id [String] Identifier of the debit response.
 * @property amount [Int] The amount of the debit in the merchant's minor currency e.g. cents for USD.
 * @property currency [String] ISO 4217 currency code in which the merchant charges
 * @property createdAt [Date] When this debit was created.
 * @property updatedAt [Date] When this debit was most recently updated.
 */
data class SimulateDebitResponse(
    val id: String,
    val amount: Int,
    val currency: String,
    val createdAt: Date = Date(System.currentTimeMillis()),
    val updatedAt: Date = Date(System.currentTimeMillis()),
)
