/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sudoplatform.sudoidentityverification.SudoIdentityVerificationClient
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import com.sudoplatform.sudovirtualcards.SudoVirtualCardsClient
import com.sudoplatform.sudovirtualcards.simulator.logging.LogConstants
import com.sudoplatform.sudovirtualcards.types.ProvisionalCard
import com.sudoplatform.sudovirtualcards.types.Card
import com.sudoplatform.sudovirtualcards.types.ListOutput
import com.sudoplatform.sudovirtualcards.types.Transaction
import com.sudoplatform.sudovirtualcards.types.inputs.ProvisionCardInput
import com.sudoplatform.sudovirtualcards.types.inputs.filters.filterTransactionsBy
import com.sudoplatform.sudovirtualcards.util.LocaleUtil
import io.kotlintest.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.Calendar

/**
 * Base class of tests that register and authenticate
 *
 * @since 2020-07-24
 */
open class BaseTest {

    companion object {
        protected const val USERNAME = "ADMIN_API_USERNAME"
        protected const val PASSWORD = "ADMIN_API_PASSWORD"
        protected const val TEST_ID = "vc-sim-sdk-test"
        protected const val VERBOSE = false
    }

    protected val context: Context = ApplicationProvider.getApplicationContext<Context>()

    protected var username: String = ""
    protected var password: String = ""

    protected fun loginCredentialsPresent() = username.isNotBlank() && password.isNotBlank()

    protected lateinit var userClient: SudoUserClient
    protected lateinit var sudoClient: SudoProfilesClient
    protected lateinit var keyManager: KeyManagerInterface
    protected lateinit var vcardsClient: SudoVirtualCardsClient
    protected lateinit var idvClient: SudoIdentityVerificationClient
    protected lateinit var simulatorClient: SudoVirtualCardsSimulatorClient
    protected val logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

    protected val expirationCalendar = Calendar.getInstance()
    init {
        expirationCalendar.add(Calendar.YEAR, 1)
    }
    protected val expirationMonth: Int = expirationCalendar.get(Calendar.MONTH) + 1
    protected val expirationYear: Int = expirationCalendar.get(Calendar.YEAR)

    protected val sudosToDelete = mutableListOf<Sudo>()

    protected fun init() {
        Timber.plant(Timber.DebugTree())

        if (VERBOSE) {
            java.util.logging.Logger.getLogger("com.amazonaws").level = java.util.logging.Level.FINEST
            java.util.logging.Logger.getLogger("org.apache.http").level = java.util.logging.Level.FINEST
        }

        // Look in the instrumentation registry for the creds
        InstrumentationRegistry.getArguments()?.let { extras ->
            username = extras.getString(USERNAME, "")
            password = extras.getString(PASSWORD, "")
        }

        if (clientConfigFilesPresent()) {
            userClient = SudoUserClient.builder(context)
                .setNamespace(TEST_ID)
                .build()
            userClient.reset()

            val containerURI = Uri.fromFile(context.cacheDir)
            sudoClient = SudoProfilesClient.builder(context, userClient, containerURI)
                .build()
            keyManager = KeyManagerFactory(context).createAndroidKeyManager()

            vcardsClient = SudoVirtualCardsClient.builder()
                .setContext(context)
                .setSudoProfilesClient(sudoClient)
                .setSudoUserClient(userClient)
                .setLogger(logger)
                .build()

            idvClient = SudoIdentityVerificationClient.builder(context, userClient)
                .build()
            idvClient.reset()
        }

        simulatorClient = SudoVirtualCardsSimulatorClient.builder()
            .setContext(context)
            .setUsername(username)
            .setPassword(password)
            .setLogger(logger)
            .build()
    }

    protected fun fini() = runBlocking {
        if (clientConfigFilesPresent()) {
            if (userClient.isRegistered()) {
                sudosToDelete.forEach {
                    try {
                        sudoClient.deleteSudo(it)
                    } catch (e: Throwable) {
                        Timber.e(e)
                    }
                }
                deregister()
            }
            userClient.reset()
            sudoClient.reset()
        }

        Timber.uprootAll()
    }

    protected fun clientConfigFilesPresent(): Boolean {
        val configFiles = context.assets.list("")?.filter { fileName ->
            fileName == "sudoplatformconfig.json" ||
                fileName == "register_key.private" ||
                fileName == "register_key.id"
        } ?: emptyList()
        Timber.d("config files present ${configFiles.size}")
        return configFiles.size == 3
    }

    protected suspend fun register() {
        userClient.isRegistered() shouldBe false

        val privateKey = readTextFile("register_key.private")
        val keyId = readTextFile("register_key.id")

        val authProvider = TESTAuthenticationProvider(
            name = "vc-sim-client-test",
            privateKey = privateKey,
            publicKey = null,
            keyManager = keyManager,
            keyId = keyId
        )

        userClient.registerWithAuthenticationProvider(authProvider, "vc-sim-client-test")
    }

    private fun readTextFile(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use {
            it.readText().trim()
        }
    }

    protected suspend fun deregister() {
        userClient.deregister()
    }

    protected suspend fun signIn() {
        userClient.signInWithKey()
    }

    protected suspend fun signInAndRegister() {
        userClient.isRegistered() shouldBe false
        register()
        userClient.isRegistered() shouldBe true
        signIn()
        userClient.isSignedIn() shouldBe true
    }

    protected suspend fun refreshTokens() {
        userClient.refreshTokens(userClient.getRefreshToken()!!)
    }

    protected suspend fun verifyTestUserIdentity() {

        val countryCodeAlpha3 = LocaleUtil.toCountryCodeAlpha3(context, AndroidTestData.VerifiedUser.country)
            ?: throw IllegalArgumentException("Unable to convert country code to ISO 3166 Alpha-3")

        idvClient.verifyIdentity(
            firstName = AndroidTestData.VerifiedUser.firstName,
            lastName = AndroidTestData.VerifiedUser.lastName,
            address = AndroidTestData.VerifiedUser.addressLine1,
            city = AndroidTestData.VerifiedUser.city,
            state = AndroidTestData.VerifiedUser.state,
            postalCode = AndroidTestData.VerifiedUser.postalCode,
            country = countryCodeAlpha3,
            dateOfBirth = AndroidTestData.VerifiedUser.dateOfBirth
        )
    }

    protected suspend fun createSudo(sudoInput: Sudo): Sudo {
        return sudoClient.createSudo(sudoInput)
    }

    protected suspend fun createCard(input: ProvisionCardInput): Card {
        val provisionalCard1 = vcardsClient.provisionCard(input)
        var state = provisionalCard1.state

        return withTimeout<Card>(20_000L) {
            var card: Card? = null
            while (state == ProvisionalCard.State.PROVISIONING) {
                val provisionalCard2 = vcardsClient.getProvisionalCard(provisionalCard1.id)
                if (provisionalCard2?.state == ProvisionalCard.State.COMPLETED) {
                    card = provisionalCard2.card
                } else {
                    delay(2_000L)
                }
                state = provisionalCard2?.state ?: ProvisionalCard.State.PROVISIONING
            }
            card ?: throw java.lang.AssertionError("Provisioned card should not be null")
        }
    }

    protected suspend fun listTransactions(card: Card): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        var listOutput: ListOutput<Transaction>? = null
        while (listOutput == null || listOutput.nextToken != null) {
            listOutput = vcardsClient.listTransactions(nextToken = listOutput?.nextToken) {
                filterTransactionsBy {
                    cardId equalTo card.id
                }
            }
            transactions.addAll(listOutput.items)
        }
        return transactions
    }
}
