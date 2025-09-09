/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.appsync

import android.content.Context
import com.amazonaws.regions.Regions
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.ApiCategoryConfiguration
import com.amplifyframework.api.aws.AWSApiPlugin
import com.amplifyframework.api.aws.ApiAuthProviders
import com.appmattus.certificatetransparency.certificateTransparencyInterceptor
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudouser.amplify.GraphQLClient
import com.sudoplatform.sudouser.http.ConvertClientErrorsInterceptor
import com.sudoplatform.sudovirtualcards.simulator.auth.SimulatorCognitoUserPoolAuthProvider
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Objects
import java.util.concurrent.TimeUnit

/**
 * A factory for [GraphQLClient]
 */
internal class GraphQLClientFactory {
    companion object {
        /**
         * Create a [GraphQLClient] by loading configuration and setting up the required
         * authentication provider.
         *
         * @throws [NullPointerException] if the required arguments or configuration information is missing.
         */
        @Throws(NullPointerException::class)
        fun getGraphQLClient(
            context: Context?,
            username: String?,
            password: String?,
            apiKey: String?,
        ): GraphQLClient {
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

            var authProviders: ApiAuthProviders? = null
            var authorization = ""
            if (username != null && password != null) {
                authorization = "'authorizationType': 'AMAZON_COGNITO_USER_POOLS'"
                val authProvider =
                    SimulatorCognitoUserPoolAuthProvider(
                        context = context,
                        poolId = poolId!!,
                        clientId = clientId!!,
                        region = region!!,
                        username = username,
                        password = password,
                    )
                authProviders = ApiAuthProviders.builder().cognitoUserPoolsAuthProvider(authProvider).build()
            }
            if ((apiKey != null)) {
                authorization = "'authorizationType': 'API_KEY', 'apiKey': '$apiKey'"
            }
            val graphqlConfig =
                JSONObject(
                    """
                    {
                        'plugins': {
                            'awsAPIPlugin': {
                                'Simulator': {
                                    'endpointType': 'GraphQL',
                                    'endpoint': '$apiUrl',
                                    'region': '${Regions.fromName(region)}',
                                    $authorization
                                }
                            },
                            'awsCognitoAuthPlugin': {
                                'CognitoUserPool': {
                                    'Default': {
                                        'PoolId': '$poolId',
                                        'AppClientId': '$clientId',
                                        "Region": "'${Regions.fromName(region)}'"
                                    }
                                }
                            }
                        }
                    }
                    """.trimIndent(),
                )

            val apiCategoryConfiguration = ApiCategoryConfiguration()
            apiCategoryConfiguration.populateFromJSON(graphqlConfig)
            val apiCategory = ApiCategory()
            val pluginBuilder =
                AWSApiPlugin
                    .builder()
                    .configureClient(
                        "Simulator",
                    ) { builder -> this.buildOkHttpClient(builder) }
            if (authProviders != null) {
                pluginBuilder.apiAuthProviders(authProviders)
            }

            val awsApiPlugin = pluginBuilder.build()

            apiCategory.addPlugin(awsApiPlugin)
            apiCategory.configure(apiCategoryConfiguration, context)
            apiCategory.initialize(context)

            return GraphQLClient(apiCategory)
        }

        /**
         * Construct the [OkHttpClient] configured with the certificate transparency checking interceptor.
         */
        private fun buildOkHttpClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
            val interceptor = certificateTransparencyInterceptor {}
            val httpClientBuilder =
                builder
                    .readTimeout(30L, TimeUnit.SECONDS)
                    .apply {
                        // Convert exceptions which are swallowed by the GraphQLOperation error manager
                        // into ones we can detect
                        addInterceptor(ConvertClientErrorsInterceptor())

                        // Certificate transparency checking
                        addNetworkInterceptor(interceptor)
                    }
            return httpClientBuilder
        }
    }
}
