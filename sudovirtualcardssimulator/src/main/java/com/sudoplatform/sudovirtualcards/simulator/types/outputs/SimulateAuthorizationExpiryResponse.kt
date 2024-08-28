/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import java.util.Date

/**
 * An authorization expiry was requested in the simulator.
 *
 * @property id [String] Identifier of this authorization expiry response.
 * @property createdAt [Date] When this authorization was created.
 * @property updatedAt [Date] When this authorization was most recently updated.
 */
data class SimulateAuthorizationExpiryResponse(
    val id: String,
    val createdAt: Date = Date(System.currentTimeMillis()),
    val updatedAt: Date = Date(System.currentTimeMillis()),
)
