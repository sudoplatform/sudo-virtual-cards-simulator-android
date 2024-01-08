/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import java.util.Date

/**
 * An authorization created in the simulator.
 *
 * @property id [String] Identifier of this authorization response. If approved, [id] may be used in subsequent incremental authorizations,
 * reversals and debits.
 * @property isApproved [Boolean] True if the transaction is authorized, false if it is declined.
 * @property amount [Int] The amount of the transaction being authorized or declined in the merchant's minor currency e.g. cents for USD.
 * @property currency [String] ISO 4217 currency code in which the merchant charges
 * @property declineReason [String] Why the transaction is being declined. Null if it is being authorized.
 * @property createdAt [Date] When this authorization was created.
 * @property updatedAt [Date] When this authorization was most recently updated.
 */
data class SimulateAuthorizationResponse(
    val id: String,
    val isApproved: Boolean,
    val amount: Int? = null,
    val currency: String? = null,
    val declineReason: String? = null,
    val createdAt: Date = Date(System.currentTimeMillis()),
    val updatedAt: Date = Date(System.currentTimeMillis()),
)
