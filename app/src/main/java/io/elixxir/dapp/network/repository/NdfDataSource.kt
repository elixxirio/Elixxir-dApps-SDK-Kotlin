package io.elixxir.dapp.network.repository

import androidx.annotation.RawRes
import io.elixxir.dapp.api.model.CommonProperties
import io.elixxir.dapp.api.model.RetryStrategy
import io.elixxir.dapp.bindings.data.Bindings
import io.elixxir.dapp.network.model.NdfSettings
import kotlinx.coroutines.*
typealias Ndf = String

internal interface NdfDataSource {
    suspend fun fetchNdf(): Ndf
}

internal class RemoteNdfDataSource private constructor(
    properties: CommonProperties,
    private val settings: NdfSettings,
    private val bindings: Bindings
) : NdfDataSource,
    CommonProperties by properties,
    RetryStrategy by settings.retryStrategy
{

    override suspend fun fetchNdf(): Ndf {
        return retryFetchNdf()
    }

    private suspend fun retryFetchNdf(retries: Int = 0): Ndf = withContext(dispatcher) {
        val ndf = downloadAndVerifySignedNdfWithUrl(
            settings.ndfUrl,
            readCertificate(settings.certificateRef)
        )

        if (ndf.isEmpty() && retries <= maxRetries) {
            ensureActive()
            delay(retryDelayMs)
            retryFetchNdf(retries + 1)
        } else {
            ndf
        }
    }

    private fun downloadAndVerifySignedNdfWithUrl(
        url: String,
        certificate: String
    ): Ndf = bindings.fetchSignedNdf(url, certificate)

    private fun readCertificate(@RawRes certRef: Int): String {
        val reader= context().resources.openRawResource(certRef)
            .bufferedReader()
        return reader.use { reader.readText() }
    }

    companion object {
        internal fun newInstance(
            properties: CommonProperties,
            settings: NdfSettings,
            bindings: Bindings
        ) = RemoteNdfDataSource(properties, settings, bindings)
    }
}