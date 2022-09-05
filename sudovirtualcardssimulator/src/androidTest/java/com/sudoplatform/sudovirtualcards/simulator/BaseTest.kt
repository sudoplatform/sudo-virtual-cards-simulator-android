/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.stripe.android.Stripe
import com.sudoplatform.sudoentitlements.SudoEntitlementsClient
import com.sudoplatform.sudoidentityverification.DefaultSudoIdentityVerificationClient
import com.sudoplatform.sudoidentityverification.types.inputs.VerifyIdentityInput
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.DefaultSudoProfilesClient
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudouser.DefaultSudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import com.sudoplatform.sudovirtualcards.SudoVirtualCardsClient
import com.sudoplatform.sudovirtualcards.simulator.logging.LogConstants
import com.sudoplatform.sudovirtualcards.simulator.util.StripeIntentWorker
import com.sudoplatform.sudovirtualcards.types.FundingSource
import com.sudoplatform.sudovirtualcards.types.JsonValue
import com.sudoplatform.sudovirtualcards.types.ListAPIResult
import com.sudoplatform.sudovirtualcards.types.ProvisionalVirtualCard
import com.sudoplatform.sudovirtualcards.types.Transaction
import com.sudoplatform.sudovirtualcards.types.VirtualCard
import com.sudoplatform.sudovirtualcards.types.inputs.CompleteFundingSourceInput
import com.sudoplatform.sudovirtualcards.types.inputs.CreditCardFundingSourceInput
import com.sudoplatform.sudovirtualcards.types.inputs.FundingSourceType
import com.sudoplatform.sudovirtualcards.types.inputs.ProvisionVirtualCardInput
import com.sudoplatform.sudovirtualcards.types.inputs.SetupFundingSourceInput
import com.sudoplatform.sudovirtualcards.util.LocaleUtil
import io.kotlintest.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.Calendar

/**
 * Base class of tests that register and authenticate
 */
open class BaseTest {

    companion object {
        protected const val TEST_ID = "vc-sim-sdk-test"
        protected const val VERBOSE = false
    }

    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    private var apiKey: String = ""

    protected fun apiKeyPresent() = apiKey.isNotBlank()

    private val userClient by lazy {
        DefaultSudoUserClient(context, TEST_ID)
    }

    private val sudoClient by lazy {
        val containerURI = Uri.fromFile(context.cacheDir)
        DefaultSudoProfilesClient(context, userClient, containerURI)
    }

    private val keyManager by lazy {
        KeyManagerFactory(context).createAndroidKeyManager(TEST_ID)
    }

    private val entitlementsClient by lazy {
        SudoEntitlementsClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .build()
    }

    protected val vcClient by lazy {
        SudoVirtualCardsClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setLogger(logger)
            .build()
    }

    private val idvClient by lazy {
        DefaultSudoIdentityVerificationClient(context, userClient)
    }

    protected lateinit var simulatorClient: SudoVirtualCardsSimulatorClient
    protected val logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

    protected val expirationCalendar: Calendar = Calendar.getInstance()
    init {
        expirationCalendar.add(Calendar.YEAR, 1)
    }
    protected val expirationMonth: Int = expirationCalendar.get(Calendar.MONTH) + 1
    protected val expirationYear: Int = expirationCalendar.get(Calendar.YEAR)

    protected fun init() {
        Timber.plant(Timber.DebugTree())

        if (VERBOSE) {
            java.util.logging.Logger.getLogger("com.amazonaws").level = java.util.logging.Level.FINEST
            java.util.logging.Logger.getLogger("org.apache.http").level = java.util.logging.Level.FINEST
        }

        apiKey = readTextFile("api.key")

        simulatorClient = SudoVirtualCardsSimulatorClient.builder()
            .setContext(context)
            .setApiKey(apiKey)
            .setLogger(logger)
            .build()
    }

    protected fun fini() = runBlocking {
        if (clientConfigFilesPresent()) {
            if (userClient.isRegistered()) {
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
                fileName == "register_key.id" ||
                fileName == "api.key"
        } ?: emptyList()
        Timber.d("config files present ${configFiles.size}")
        return configFiles.size == 4
    }

    private suspend fun register() {
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

    private suspend fun deregister() {
        userClient.deregister()
    }

    private suspend fun signIn() {
        userClient.signInWithKey()
    }

    protected suspend fun signInAndRegister() {
        userClient.isRegistered() shouldBe false
        register()
        userClient.isRegistered() shouldBe true
        signIn()
        userClient.isSignedIn() shouldBe true
        entitlementsClient.redeemEntitlements()
    }

    protected suspend fun refreshTokens() {
        userClient.refreshTokens(userClient.getRefreshToken()!!)
    }

    protected suspend fun verifyTestUserIdentity() {

        val countryCodeAlpha3 = LocaleUtil.toCountryCodeAlpha3(context, AndroidTestData.VerifiedUser.country)
            ?: throw IllegalArgumentException("Unable to convert country code to ISO 3166 Alpha-3")

        val verifyIdentityInput = VerifyIdentityInput(
            AndroidTestData.VerifiedUser.firstName,
            AndroidTestData.VerifiedUser.lastName,
            AndroidTestData.VerifiedUser.addressLine1,
            AndroidTestData.VerifiedUser.city,
            AndroidTestData.VerifiedUser.state,
            AndroidTestData.VerifiedUser.postalCode,
            countryCodeAlpha3,
            AndroidTestData.VerifiedUser.dateOfBirth
        )
        idvClient.verifyIdentity(verifyIdentityInput)
    }

    protected suspend fun createSudo(sudoInput: Sudo): Sudo {
        return sudoClient.createSudo(sudoInput)
    }

    protected suspend fun getOwnershipProof(sudo: Sudo): String {
        return sudoClient.getOwnershipProof(sudo, "sudoplatform.virtual-cards.virtual-card")
    }

    protected suspend fun createFundingSource(client: SudoVirtualCardsClient, input: CreditCardFundingSourceInput): FundingSource {

        // Retrieve the funding source client configuration
        val configuration = client.getFundingSourceClientConfiguration()

        // Perform the funding source setup operation
        val setupInput = SetupFundingSourceInput("USD", FundingSourceType.CREDIT_CARD)
        val provisionalFundingSource = client.setupFundingSource(setupInput)

        // Process stripe data
        val stripeClient = Stripe(context, configuration.first().apiKey)
        val stripeIntentWorker = StripeIntentWorker(context, stripeClient)
        val completionData = stripeIntentWorker.confirmSetupIntent(
            input,
            provisionalFundingSource.provisioningData.clientSecret
        )

        // Perform the funding source completion operation
        val completeInput = CompleteFundingSourceInput(
            provisionalFundingSource.id,
            completionData,
            null
        )
        return client.completeFundingSource(completeInput)
    }

    protected suspend fun createCard(input: ProvisionVirtualCardInput): VirtualCard {
        val provisionalCard1 = vcClient.provisionVirtualCard(input)
        var state = provisionalCard1.provisioningState

        return withTimeout<VirtualCard>(20_000L) {
            var card: VirtualCard? = null
            while (state == ProvisionalVirtualCard.ProvisioningState.PROVISIONING) {
                val provisionalCard2 = vcClient.getProvisionalCard(provisionalCard1.id)
                if (provisionalCard2?.provisioningState == ProvisionalVirtualCard.ProvisioningState.COMPLETED) {
                    card = provisionalCard2.card
                } else {
                    delay(2_000L)
                }
                state = provisionalCard2?.provisioningState ?: ProvisionalVirtualCard.ProvisioningState.PROVISIONING
            }
            card ?: throw AssertionError("Provisioned card should not be null")
        }
    }

    protected suspend fun listTransactions(card: VirtualCard): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        var listOutput: List<Transaction>? = null
        var nextToken: String? = null

        while (listOutput == null || nextToken != null) {
            when (val listResult = vcClient.listTransactionsByCardId(card.id)) {
                is ListAPIResult.Success -> {
                    listOutput = listResult.result.items
                    nextToken = listResult.result.nextToken
                    transactions.addAll(listOutput)
                }
                else -> { throw AssertionError("Unexpected ListAPIResult") }
            }
        }
        return transactions
    }

    protected suspend fun setupVirtualCardResources(inputFundingSource: FundingSource? = null): VirtualCard {
        val fundingSource: FundingSource
        if (inputFundingSource == null) {
            // Create a funding source
            val fundingSourceInput = CreditCardFundingSourceInput(
                AndroidTestData.Visa.cardNumber,
                expirationMonth,
                expirationYear,
                AndroidTestData.Visa.securityCode,
                AndroidTestData.VerifiedUser.addressLine1,
                AndroidTestData.VerifiedUser.addressLine2,
                AndroidTestData.VerifiedUser.city,
                AndroidTestData.VerifiedUser.state,
                AndroidTestData.VerifiedUser.postalCode,
                AndroidTestData.VerifiedUser.country
            )
            fundingSource = createFundingSource(vcClient, fundingSourceInput)
        } else {
            fundingSource = inputFundingSource
        }

        // Create a Sudo
        val sudo = createSudo(AndroidTestData.sudo)

        // Create a virtual card
        val ownershipProof = getOwnershipProof(sudo)
        vcClient.createKeysIfAbsent()
        val cardInput = ProvisionVirtualCardInput(
            ownershipProofs = listOf(ownershipProof),
            fundingSourceId = fundingSource.id,
            cardHolder = AndroidTestData.VirtualUser.cardHolder,
            metadata = JsonValue.JsonString(AndroidTestData.VirtualUser.alias),
            addressLine1 = AndroidTestData.VirtualUser.addressLine1,
            city = AndroidTestData.VirtualUser.city,
            state = AndroidTestData.VirtualUser.state,
            postalCode = AndroidTestData.VirtualUser.postalCode,
            country = AndroidTestData.VirtualUser.country,
            currency = "USD"
        )
        return createCard(cardInput)
    }
}
