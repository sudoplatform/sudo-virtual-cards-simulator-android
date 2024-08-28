/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import com.sudoplatform.sudovirtualcards.simulator.rules.ActualPropertyResetter
import com.sudoplatform.sudovirtualcards.simulator.rules.PropertyResetter
import com.sudoplatform.sudovirtualcards.simulator.rules.TimberLogRule
import org.junit.Rule

/**
 * Base class that sets up:
 * - [TimberLogRule]
 * - [com.sudoplatform.sudovirtualcards.simulator.rules.PropertyResetRule]
 *
 * And provides convenient access to the [com.sudoplatform.sudovirtualcards.simulator.rules.PropertyResetRule.before].
 */
abstract class BaseTests : PropertyResetter by ActualPropertyResetter() {
    @Rule @JvmField
    val timberLogRule = TimberLogRule()
}
