package me.devnatan.katan.common.server

import me.devnatan.katan.api.server.ServerComposition
import me.devnatan.katan.api.server.ServerCompositions

class ServerCompositionsImpl : ServerCompositions {

    private val lock = Any()
    private val registered = linkedMapOf<ServerComposition.Key<*>, ServerComposition<*>>()

    override operator fun <T : ServerComposition<*>> get(key: ServerComposition.Key<T>): T? {
        return synchronized(lock) {
            registered[key] as? T
        }
    }

    operator fun set(key: ServerComposition.Key<*>, composition: ServerComposition<*>) {
        return synchronized(lock) {
            registered[key] = composition
        }
    }

    override fun iterator(): Iterator<ServerComposition<*>> {
        return registered.values.iterator()
    }

}