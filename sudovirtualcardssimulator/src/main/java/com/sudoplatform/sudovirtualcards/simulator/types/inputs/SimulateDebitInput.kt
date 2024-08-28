/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

/**
 * A request to create a debit in the simulator.
 *
 * @property authorizationId [String] The identifier of the authorization created for this debit
 * @property amount [Int] Amount of transaction in merchant's minor currency, e.g. cents for USD
 */
data class SimulateDebitInput(
    val authorizationId: String,
    val amount: Int,
)
