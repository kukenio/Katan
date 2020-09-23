package me.devnatan.katan.api.server

/**
 * @author Natan V.
 * @since 0.1.0
 * @property id the container identification.
 */
open class ServerContainer(val id: String) {

    /**
     * Container inspection result.
     */
    var inspection: ServerInspection = ServerInspection.Uninspected

    /**
     * Checks if the container has already been inspected.
     * @return [ServerInspection.Uninspected] if it hasn't been inspected.
     */
    open fun isInspected(): Boolean {
        return synchronized(inspection) {
            inspection !is ServerInspection.Uninspected
        }
    }

}

/**
 * @author Natan V.
 * @since 0.1.0
 */
interface ServerInspection {

    /**
     * Represents an inspection result not yet performed.
     */
    object Uninspected : ServerInspection

}