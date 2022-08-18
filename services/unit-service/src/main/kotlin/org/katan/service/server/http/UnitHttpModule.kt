package org.katan.service.server.http

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import org.katan.http.di.HttpModule
import org.katan.http.di.HttpModuleRegistry
import org.katan.service.server.http.routes.createUnit
import org.katan.service.server.http.routes.getUnit
import org.katan.service.server.http.routes.getUnitAuditLogs
import org.katan.service.server.http.routes.listUnits
import org.katan.service.server.http.routes.modifyUnit

internal class UnitHttpModule(registry: HttpModuleRegistry) : HttpModule(registry) {

    override fun install(app: Application) {
        app.routing {
            authenticate {
                listUnits()
                getUnit()
                modifyUnit()
                createUnit()
                getUnitAuditLogs()
            }
        }
    }
}
