/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import java.util.Date

/**
 * An authorization expiry was requested in the simulator.
 *
 * @property id Identifier of this authorization expiry response.
 * @property createdAt When this authorization was created.
 * @property updatedAt When this authorization was most recently updated.
 *
 * @since 2020-07-07
 */
data class SimulateAuthorizationExpiryResponse(
    val id: String,
    val createdAt: Date = Date(System.currentTimeMillis()),
    val updatedAt: Date = Date(System.currentTimeMillis())
)
