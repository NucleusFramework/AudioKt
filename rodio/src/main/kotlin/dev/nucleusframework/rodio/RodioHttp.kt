package dev.nucleusframework.rodio

import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeHttpAddRootCertPem
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeHttpClearRootCerts
import dev.nucleusframework.rodio.internal.NativeRodioBridge.nativeHttpSetAllowInvalidCerts

/**
 * Global HTTP options applied to every rodio network request (streaming and
 * download). Settings are process-wide and take effect on the next request.
 */
object RodioHttp {
    fun setAllowInvalidCerts(allow: Boolean) {
        nativeHttpSetAllowInvalidCerts(allow)
    }

    fun addRootCertPem(pem: String) {
        nativeHttpAddRootCertPem(pem)
    }

    fun clearRootCerts() {
        nativeHttpClearRootCerts()
    }
}