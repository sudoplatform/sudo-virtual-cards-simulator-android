/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

/**
 * A request to create the reversal of an authorization in the simulator.
 *
 * @property authorizationId The identifier of the authorization to which this reversal applies
 * @property amount Amount of transaction in merchant's minor currency, e.g. cents for USD
 *
 * @since 2020-07-02
 */
data class SimulateReversalInput(
    val authorizationId: String,
    val amount: Int
)
