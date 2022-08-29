/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

/**
 * A request to create a refund in the simulator.
 *
 * @property debitId [String] The identifier of the debit to which this refund applies
 * @property amount [Int] Amount of transaction in merchant's minor currency, e.g. cents for USD
 */
data class SimulateRefundInput(
    val debitId: String,
    val amount: Int
)
