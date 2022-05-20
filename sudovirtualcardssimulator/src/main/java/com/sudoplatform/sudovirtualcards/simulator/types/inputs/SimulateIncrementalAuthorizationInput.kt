/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.inputs

/**
 * A request to simulate an incremental authorization request from a merchant.
 *
 * @property authorizationId The identifier of the previous successful authorization to which this incremental authorization corresponds.
 * @property amount Amount to increase the authorization amount by in merchant's minor currency, e.g. cents for USD
 */
data class SimulateIncrementalAuthorizationInput(
    val authorizationId: String,
    val amount: Int
)
