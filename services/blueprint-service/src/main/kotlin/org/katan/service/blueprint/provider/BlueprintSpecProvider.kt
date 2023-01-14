package org.katan.service.blueprint.provider

import org.katan.model.blueprint.BlueprintSpec

interface BlueprintSpecProvider {

    val id: String

    suspend fun provide(source: BlueprintSpecSource): BlueprintSpec
}
