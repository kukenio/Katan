package org.katan.service.unit.instance.repository

import org.katan.model.unit.UnitInstance
import org.katan.model.unit.UnitInstanceStatus

public interface UnitInstanceRepository {

    public suspend fun findById(id: Long): UnitInstance?

    public suspend fun create(instance: UnitInstance)

    public suspend fun updateStatus(
        id: Long,
        status: UnitInstanceStatus
    )

    public suspend fun delete(id: Long)

}