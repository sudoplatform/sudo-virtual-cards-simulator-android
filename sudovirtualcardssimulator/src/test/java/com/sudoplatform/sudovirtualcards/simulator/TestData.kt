/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

/**
 * Data used in tests.
 */
object TestData {

    /** Test user that is pre-verified */
    object VerifiedUser {
        const val firstName = "John"
        const val lastName = "Smith"
        const val fullName = "$firstName $lastName"
        const val addressLine1 = "222333 Peachtree Place"
        val addressLine2 = null
        const val city = "Atlanta"
        const val state = "GA"
        const val postalCode = "30318"
        const val country = "US"
        const val dateOfBirth = "1975-02-28"
    }
}
