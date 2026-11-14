package io.grimoire.desktop

import java.util.Properties

/** Generated from the same catalog values that resolve the host's dependencies. */
object HostRuntime {
    private val properties = Properties().apply {
        checkNotNull(HostRuntime::class.java.getResourceAsStream("/host.properties")).use(::load)
    }
    val apiVersion: String = checkNotNull(properties.getProperty("api.version"))
    val jsonCoordinate: String = checkNotNull(properties.getProperty("json.coordinate"))
}
