/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import java.util.Date

/**
 * A reversal of a debit created in the simulator.
 *
 * @property id [String] Identifier of the reversal.
 * @property amount [Int] The amount of the reversal in the merchant's minor currency e.g. cents for USD.
 * @property currency [String] ISO 4217 currency code in which the merchant charges
 * @property createdAt [Date] When this reversal was created.
 * @property updatedAt [Date] When this reversal was most recently updated.
 */
data class SimulateReversalResponse(
    val id: String,
    val amount: Int,
    val currency: String,
    val createdAt: Date = Date(System.currentTimeMillis()),
    val updatedAt: Date = Date(System.currentTimeMillis()),
)
