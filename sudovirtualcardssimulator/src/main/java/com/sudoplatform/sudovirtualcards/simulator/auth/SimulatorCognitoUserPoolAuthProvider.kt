/*
 * Copyright © 2022 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudovirtualcards.simulator.auth

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.amazonaws.mobileconnectors.appsync.sigv4.CognitoUserPoolsAuthProvider
import kotlinx.coroutines.runBlocking

/**
 * Provides the authentication for all GraphQL API calls to the Simulator Service.
 *
 * This is used when the client is configured to be authenticated via Cognito User Pools.
 */
internal class SimulatorCognitoUserPoolAuthProvider(
    context: Context,
    poolId: String,
    clientId: String,
    region: String,
    private val username: String,
    private val password: String,
    @VisibleForTesting
    private val authenticator: UserPoolAuthenticator = createDefaultUserPoolAuthenticator(context, poolId, clientId, region)
) : CognitoUserPoolsAuthProvider {

    companion object {
        /**
         * Generate an AWS style configuration JSON string.
         *
         * @param poolId Identifier of the user pool to authenticate against
         * @param clientId Client Pool Id to authenticate against
         * @param region Region that the user pool resides in
         */
        private fun generateConfiguration(poolId: String, clientId: String, region: String) =
            """{
                "CognitoUserPool": {
                    "Default": {
                        "PoolId": "$poolId",
                        "AppClientId": "$clientId",
                        "Region": "$region"
                    }
                }
            }
            """.trimIndent()

        private fun createDefaultUserPoolAuthenticator(
            context: Context,
            poolId: String,
            clientId: String,
            region: String
        ): UserPoolAuthenticator {
            return AWSUserPoolAuthenticator(
                context,
                generateConfiguration(poolId = poolId, clientId = clientId, region = region)
            )
        }
    }

    override fun getLatestAuthToken(): String {
        return runBlocking<String> {

            authenticator.initialize()

            if (authenticator.state != UserPoolAuthenticator.State.SIGNED_IN) {
                authenticator.signIn(username, password)
            }

            if (authenticator.state == UserPoolAuthenticator.State.SIGNED_IN) {
                return@runBlocking authenticator.getTokens()?.accessToken?.tokenString
                    ?: throw IllegalStateException("Null authentication token")
            }

            throw IllegalStateException("Failed to authenticate with Cognito")
        }
    }
}
