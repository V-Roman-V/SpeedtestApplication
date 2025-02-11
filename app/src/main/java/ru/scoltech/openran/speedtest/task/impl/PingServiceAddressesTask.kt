package ru.scoltech.openran.speedtest.task.impl

import io.swagger.client.model.ServerAddr
import ru.scoltech.openran.speedtest.backend.IcmpPinger
import ru.scoltech.openran.speedtest.task.Task
import ru.scoltech.openran.speedtest.util.Promise
import ru.scoltech.openran.speedtest.util.TaskKiller

data class PingServiceAddressesTask(
    private val timeout: Long,
    private val onPingUpdate: (Long) -> Unit
) : Task<List<ServerAddr>, ServerAddr> {
    /**
     * @param argument Service addresses
     */
    override fun prepare(
        argument: List<ServerAddr>,
        killer: TaskKiller
    ): Promise<(ServerAddr) -> Unit, (String, Exception?) -> Unit> = Promise { onSuccess, onError ->
        // TODO support many addresses
        val address = argument[0]
        val icmpPinger = IcmpPinger()

        killer.register { icmpPinger.stop() }
        icmpPinger.pingOnce(address.ip, timeout)
            .onSuccess {
                onPingUpdate(it)
                onSuccess?.invoke(address)
            }
            .onError { onError?.invoke("Could not get service ping", it) }
            .start()
    }
}
