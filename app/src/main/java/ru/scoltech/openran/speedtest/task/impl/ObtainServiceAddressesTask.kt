package ru.scoltech.openran.speedtest.task.impl

import com.squareup.okhttp.HttpUrl
import io.swagger.client.model.ServerAddressResponse
import ru.scoltech.openran.speedtest.backend.addPathSegments
import ru.scoltech.openran.speedtest.task.FatalException
import ru.scoltech.openran.speedtest.task.Task
import ru.scoltech.openran.speedtest.util.Promise
import ru.scoltech.openran.speedtest.util.TaskKiller
import java.net.InetSocketAddress

data class ObtainServiceAddressesTask(
    private val balancerApiBuilder: BalancerApiBuilder,
    private val balancerPathSegments: List<String> = DEFAULT_BALANCER_REQUEST_PATH_SEGMENTS,
) : Task<InetSocketAddress, ServerAddressResponse> {
    /**
     * @param argument Balancer address
     */
    override fun prepare(
        argument: InetSocketAddress,
        killer: TaskKiller
    ): Promise<(ServerAddressResponse) -> Unit, (String, Exception?) -> Unit> {
        return Promise { onSuccess, onError ->
            val balancerAddress = HttpUrl.Builder()
                .scheme("http")
                .host(argument.address.hostAddress)
                .port(argument.port)
                .addPathSegments(balancerPathSegments)
                .build()
                .toString()

            try {
                val call = BalancerApi(balancerApiBuilder.setBasePath(balancerAddress))
                    .serviceAcquireCreateAsync(AcquireServiceCallback(onSuccess, onError))
                killer.register {
                    call.cancel()
                }
            } catch (e: BalancerApiException) {
                throw FatalException("Could not create balancer api call", e)
            }
        }
    }

    private inner class AcquireServiceCallback(
        private val onSuccess: ((ServerAddressResponse) -> Unit)?,
        private val onError: ((String, BalancerApiException?) -> Unit)?
    ) : BalancerApiCallback<ServerAddressResponse> {
        override fun onFailure(
            e: BalancerApiException?,
            statusCode: Int,
            responseHeaders: MutableMap<String, MutableList<String>>?
        ) {
            val statusCodeMessage = if (statusCode != 0) {
                " (status code = $statusCode)"
            } else {
                ""
            }
            onError?.invoke("Could not connect to balancer$statusCodeMessage", e)
        }

        override fun onSuccess(
            result: ServerAddressResponse,
            statusCode: Int,
            responseHeaders: MutableMap<String, MutableList<String>>
        ) {
            onSuccess?.invoke(result)
        }

        override fun onUploadProgress(bytesWritten: Long, contentLength: Long, done: Boolean) {
            // no operations
        }

        override fun onDownloadProgress(bytesRead: Long, contentLength: Long, done: Boolean) {
            // no operations
        }
    }

    companion object {
        private val DEFAULT_BALANCER_REQUEST_PATH_SEGMENTS: List<String> = listOf(
            "Skoltech_OpenRAN_5G", "iperf_load_balancer", io.swagger.client.Version.VERSION
        )
    }
}
