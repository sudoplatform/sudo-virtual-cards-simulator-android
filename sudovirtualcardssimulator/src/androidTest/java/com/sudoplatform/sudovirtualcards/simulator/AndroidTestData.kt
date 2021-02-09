/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import com.sudoplatform.sudoprofiles.Sudo

/**
 * Data used in Android tests.
 *
 * @since 2020-06-24
 */
object AndroidTestData {

    /** Test user that is pre-verified */
    object VerifiedUser {
        const val firstName = "John"
        const val lastName = "Smith"
        val fullName = "$firstName $lastName"
        const val addressLine1 = "222333 Peachtree Place"
        val addressLine2 = null
        const val city = "Atlanta"
        const val state = "GA"
        const val postalCode = "30318"
        const val country = "US"
        const val dateOfBirth = "1975-02-28"
    }

    object VirtualUser {
        const val cardHolder = "Unlimited Cards"
        const val alias = "Ted Bear"
        const val addressLine1 = "123 Nowhere St"
        const val city = "Menlo Park"
        const val state = "CA"
        const val postalCode = "94025"
        const val country = "US"
    }

    val sudo = Sudo(
        title = "Mr",
        firstName = "Theodore",
        lastName = "Bear",
        label = "Shopping",
        notes = null,
        avatar = null
    )

    object IdentityVerification {
        const val virtualCardsAudience = "sudoplatform.virtual-cards.virtual-card"
    }

    object Visa {
        const val cardNumber = "4242424242424242"
        const val securityCode = "123"
    }
}
