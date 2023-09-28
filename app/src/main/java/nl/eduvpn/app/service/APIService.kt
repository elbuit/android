/*
 *  This file is part of eduVPN.
 *
 *     eduVPN is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     eduVPN is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with eduVPN.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.eduvpn.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.eduvpn.app.utils.Log
import nl.eduvpn.app.utils.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST
import java.io.IOException

/**
 * This service is responsible for fetching data from API endpoints.
 * Created by Daniel Zolnai on 2016-10-12.
 */
class APIService(private val okHttpClient: OkHttpClient) {

    class UserNotAuthorizedException : Exception()

    /**
     * Downloads a byte resource from a URL.
     *
     * @param url         The URL as a string.
     * @param requestData The request data, if any.
     * @param accessToken The access token to fetch the resource with. Can be null.
     * @return Response with headers.
     * @throws UserNotAuthorizedException
     * @throws IOException Thrown if there was a problem creating the connection.
     */
    private suspend fun fetchByteResource(
        url: String,
        requestData: String?,
        accessToken: String?
    ): Pair<String, Map<String, List<String>>> {
        val requestBuilder = createRequestBuilder(url, accessToken)
        if (requestData != null) {
            requestBuilder.post(
                requestData.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
            )
        } else {
            requestBuilder.post(EMPTY_REQUEST)
        }
        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).await()
        val statusCode = response.code
        if (statusCode == STATUS_CODE_UNAUTHORIZED) {
            throw UserNotAuthorizedException()
        }
        // Get the body of the response
        val responseBody = response.body
        if (responseBody == null) {
            throw IOException("Response body is empty!")
        } else {
            val result = withContext(Dispatchers.IO) { responseBody.string() }
            responseBody.close()
            Log.d(TAG, "POST $url data: '$requestData': $result")
            if (statusCode in 200..299) {
                return Pair(
                    result,
                    response.headers.toMultimap()
                )
            } else {
                throw IOException("Unsuccessful response with status code $statusCode: $result")
            }
        }
    }

    /**
     * Creates a new URL connection based on the URL.
     *
     * @param urlString   The URL as a string.
     * @param accessToken The access token to fetch the resource with. Can be null.
     * @return The URL connection which can be used to connect to the URL.
     */
    private fun createRequestBuilder(urlString: String, accessToken: String?): Request.Builder {
        var builder = Request.Builder().get().url(urlString)
        if (accessToken != null && accessToken.isNotEmpty()) {
            builder = builder.header(HEADER_AUTHORIZATION, "Bearer $accessToken")
        }
        return builder
    }

    /**
     * Fetches a JSON resource from a specific URL.
     *
     * @param url The URL as a string.
     * @return The JSON resource if the call was successful.
     * @throws UserNotAuthorizedException
     * @throws IOException   Thrown if there was a problem while connecting.
     */
    private suspend fun fetchString(url: String, accessToken: String?): String {
        val requestBuilder = createRequestBuilder(url, accessToken)
        val response = okHttpClient.newCall(requestBuilder.build()).await()
        val statusCode = response.code
        if (statusCode == STATUS_CODE_UNAUTHORIZED) {
            throw UserNotAuthorizedException()
        }
        // Get the body of the response
        val responseBody = response.body
        if (responseBody == null) {
            throw IOException("Response body is empty!")
        } else {
            val responseString = withContext(Dispatchers.IO) { responseBody.string() }
            responseBody.close()
            Log.d(TAG, "GET $url: $responseString")
            if (statusCode !in 200..299) {
                throw IOException("Unsuccessful response with status code $statusCode: $responseString")
            }
            return responseString
        }
    }

    companion object {
        private val TAG = APIService::class.java.name
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val STATUS_CODE_UNAUTHORIZED = 401
    }
}
