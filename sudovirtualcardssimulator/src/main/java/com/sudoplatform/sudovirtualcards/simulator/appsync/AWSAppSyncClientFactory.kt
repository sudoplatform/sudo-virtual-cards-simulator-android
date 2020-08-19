/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.appsync

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.regions.Regions
import com.babylon.certificatetransparency.certificateTransparencyInterceptor
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudouser.ConvertSslErrorsInterceptor
import com.sudoplatform.sudovirtualcards.simulator.auth.SimulatorCognitoUserPoolAuthProvider
import okhttp3.OkHttpClient
import java.util.Objects

/**
 * A factory for [AWSAppSyncClient]
 *
 * @since 2020-06-09
 */
internal class AWSAppSyncClientFactory {

    companion object {

        /**
         * Create an [AWSAppSyncClient] by loading configuration and setting up the required
         * authentication provider.
         *
         * @throws [NullPointerException] if the required arguments or configuration information is missing.
         */
        @Throws(NullPointerException::class)
        fun getAppSyncClient(context: Context?, username: String?, password: String?): AWSAppSyncClient {

            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(username, "Username must be provided.")
            Objects.requireNonNull(password, "Password must be provided.")

            val configManager = DefaultSudoConfigManager(context!!)
            val adminApiConfig = configManager.getConfigSet("adminConsoleProjectService")
            Objects.requireNonNull(adminApiConfig, "Config file must supply adminApiService section")

            val apiUrl = adminApiConfig?.getString("apiUrl")
            val poolId = adminApiConfig?.getString("userPoolId")
            val clientId = adminApiConfig?.getString("clientId")
            val region = adminApiConfig?.getString("region")
            Objects.requireNonNull(poolId, "Config file adminApiService section must supply userPoolId")
            Objects.requireNonNull(clientId, "Config file adminApiService section must supply clientId")
            Objects.requireNonNull(region, "Config file adminApiService section must supply region")

            val authProvider = SimulatorCognitoUserPoolAuthProvider(
                context = context,
                poolId = poolId!!,
                clientId = clientId!!,
                region = region!!,
                username = username!!,
                password = password!!
            )

            return AWSAppSyncClient.builder()
                .context(context)
                .serverUrl(apiUrl)
                .cognitoUserPoolsAuthProvider(authProvider)
                .region(Regions.fromName(region))
                .okHttpClient(buildOkHttpClient())
                .build()
        }

        /**
         * Construct the [OkHttpClient] configured with the certificate transparency checking interceptor.
         */
        private fun buildOkHttpClient(): OkHttpClient {
            val interceptor = certificateTransparencyInterceptor {
                // Enable for AWS hosts. The doco says I can use *.* for all hosts
                // but that enhancement hasn't been released yet (v0.2.0)
                +"*.amazonaws.com"
                +"*.amazon.com"
            }
            val okHttpClient = OkHttpClient.Builder().apply {
                // Convert exceptions from certificate transparency into http errors that stop the
                // exponential backoff retrying of [AWSAppSyncClient]
                addInterceptor(ConvertSslErrorsInterceptor())

                // Certificate transparency checking
                addNetworkInterceptor(interceptor)
            }
            return okHttpClient.build()
        }
    }
}
