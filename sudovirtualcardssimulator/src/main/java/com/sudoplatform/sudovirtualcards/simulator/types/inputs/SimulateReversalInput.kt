/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

/**
 * A request to create the reversal of an authorization in the simulator.
 *
 * @property authorizationId [String] The identifier of the authorization to which this reversal applies
 * @property amount [Int] Amount of transaction in merchant's minor currency, e.g. cents for USD
 */
data class SimulateReversalInput(
    val authorizationId: String,
    val amount: Int,
)
