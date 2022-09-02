package org.katan.service.blueprint.model

import org.katan.model.blueprint.Blueprint

internal data class BlueprintImpl(
    override val id: Long,
    override val name: String,
    override val image: String
) : Blueprint