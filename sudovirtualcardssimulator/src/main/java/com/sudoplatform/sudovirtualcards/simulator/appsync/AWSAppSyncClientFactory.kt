/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.appsync

import android.content.Context
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.regions.Regions
import com.appmattus.certificatetransparency.certificateTransparencyInterceptor
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudouser.ConvertSslErrorsInterceptor
import com.sudoplatform.sudovirtualcards.simulator.auth.SimulatorCognitoUserPoolAuthProvider
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Objects

/**
 * A factory for [AWSAppSyncClient]
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
        fun getAppSyncClient(context: Context?, username: String?, password: String?, apiKey: String?): AWSAppSyncClient {

            Objects.requireNonNull(context, "Context must be provided.")

            if (username == null && password == null) {
                Objects.requireNonNull(apiKey, "API key must be provided.")
            } else if (apiKey == null) {
                Objects.requireNonNull(username, "Username must be provided.")
                Objects.requireNonNull(password, "Password must be provided.")
            }

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

            if (username != null && password != null) {
                val awsConfig = JSONObject(
                    """
                {
                    'CognitoUserPool': {
                        'Default': {
                            'PoolId': '$poolId',
                            'AppClientId': '$clientId',
                            "Region": "$region"
                        }
                    },
                    'AppSync': {
                        'Default': {
                            'ApiUrl': '$apiUrl', 'Region': '$region', 'AuthMode': 'AMAZON_COGNITO_USER_POOLS'}
                    }
                }
                    """.trimIndent()
                )
                val authProvider = SimulatorCognitoUserPoolAuthProvider(
                    context = context,
                    poolId = poolId!!,
                    clientId = clientId!!,
                    region = region!!,
                    username = username,
                    password = password
                )
                return AWSAppSyncClient.builder()
                    .context(context)
                    .serverUrl(apiUrl)
                    .cognitoUserPoolsAuthProvider(authProvider)
                    .awsConfiguration(AWSConfiguration(awsConfig))
                    .region(Regions.fromName(region))
                    .okHttpClient(buildOkHttpClient())
                    .build()
            } else {
                return AWSAppSyncClient.builder()
                    .context(context)
                    .serverUrl(apiUrl)
                    .region(Regions.fromName(region))
                    .apiKey { apiKey }
                    .okHttpClient(buildOkHttpClient())
                    .build()
            }
        }

        /**
         * Construct the [OkHttpClient] configured with the certificate transparency checking interceptor.
         */
        private fun buildOkHttpClient(): OkHttpClient {
            val interceptor = certificateTransparencyInterceptor {}
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
