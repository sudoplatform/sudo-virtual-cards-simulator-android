/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.types.outputs

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudovirtualcards.simulator.types.inputs.BillingAddress
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Test the public facing data classes can be written into and read from a [Bundle]
 */
@RunWith(AndroidJUnit4::class)
class ParcelTest {

    @Test
    fun parcellableClassesCanBeParcelledAndUnparcelled() {

        val currency = CurrencyAmount("AUD", 4200)
        val merchant = SimulatorMerchant(
            id = "id",
            name = "Teds Doggie Treats",
            description = "The yummiest dog treats around",
            mcc = "1234",
            city = "Robina",
            state = "Queensland",
            postalCode = "4230",
            country = "AU",
            currency = "AUD",
            declineAfterAuthorization = false,
            declineBeforeAuthorization = false,
            createdAt = Date(42L),
            updatedAt = Date(43L)
        )
        val billingAddress = BillingAddress(
            addressLine1 = "123 Nowhere St",
            addressLine2 = "Flat 202",
            city = "Robina",
            state = "Queensland",
            postalCode = "4230",
            country = "AU"
        )

        val bundle = Bundle()
        bundle.putParcelable("currency", currency)
        bundle.putParcelable("merchant", merchant)
        bundle.putParcelable("billingAddress", billingAddress)

        val bundle2 = Bundle(bundle)
        val currency2 = bundle2.getParcelable<CurrencyAmount>("currency")
        val merchant2 = bundle2.getParcelable<SimulatorMerchant>("merchant")
        val billingAddress2 = bundle2.getParcelable<BillingAddress>("billingAddress")

        currency2 shouldBe currency
        merchant2 shouldBe merchant
        billingAddress2 shouldBe billingAddress
    }
}
