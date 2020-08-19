/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

/**
 * A request to create a refund in the simulator.
 *
 * @property debitId The identifier of the debit to which this refund applies
 * @property amount Amount of transaction in merchant's minor currency, e.g. cents for USD
 *
 * @since 2020-07-01
 */
data class SimulateRefundInput(
    val debitId: String,
    val amount: Int
)
