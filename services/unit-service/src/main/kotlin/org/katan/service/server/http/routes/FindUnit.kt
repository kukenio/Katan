package org.katan.service.server.http.routes

import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import org.katan.http.respond
import org.katan.http.respondError
import org.katan.service.server.UnitService
import org.katan.service.server.http.UnitNotFound
import org.katan.service.server.http.UnitRoutes
import org.katan.service.server.http.dto.UnitResponse
import org.koin.ktor.ext.inject

internal fun Route.findUnit() {
    val unitService by inject<UnitService>()

    get<UnitRoutes.FindById> { parameters ->
        val unit = unitService.getUnit(parameters.id.toLong())
            ?: respondError(UnitNotFound)

        respond(UnitResponse(unit))
    }
}
