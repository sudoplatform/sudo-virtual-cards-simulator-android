/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import java.util.Date

/**
 * A refund created in the simulator.
 *
 * @property id Identifier of the refund.
 * @property amount The amount of the refund in the merchant's minor currency e.g. cents for USD.
 * @property currency ISO 4217 currency code in which the merchant charges
 * @property createdAt When this refund was created.
 * @property updatedAt When this refund was most recently updated.
 */
data class SimulateRefundResponse(
    val id: String,
    val amount: Int,
    val currency: String,
    val createdAt: Date = Date(System.currentTimeMillis()),
    val updatedAt: Date = Date(System.currentTimeMillis())
)
