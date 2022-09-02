package org.katan.service.blueprint.repository

import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.katan.model.blueprint.Blueprint

internal object BlueprintTable : LongIdTable("blueprints") {

    val name = varchar("name", length = 255)
    val image = varchar("image", length = 255)
    val createdAt = timestamp("created_at")

}

internal class BlueprintEntityImpl(id: EntityID<Long>) : LongEntity(id), BlueprintEntity {
    companion object : LongEntityClass<BlueprintEntityImpl>(BlueprintTable)

    override var name: String by BlueprintTable.name
    override var image: String by BlueprintTable.image
    override var createdAt: Instant by BlueprintTable.createdAt

    override fun getId(): Long = id.value
}

class BlueprintRepositoryImpl(
    private val database: Database
) : BlueprintRepository {

    init {
        transaction(db = database) {
            SchemaUtils.createMissingTablesAndColumns(BlueprintTable)
        }
    }

    override suspend fun findAll(): List<BlueprintEntity> {
        return newSuspendedTransaction(db = database) {
            BlueprintEntityImpl.all().notForUpdate().toList()
        }
    }

    override suspend fun find(id: Long): BlueprintEntity? {
        return newSuspendedTransaction(db = database) {
            BlueprintEntityImpl.findById(id)
        }
    }

    override suspend fun create(blueprint: Blueprint) {
        return newSuspendedTransaction(db = database) {
            BlueprintEntityImpl.new(blueprint.id) {
                name = blueprint.name
                image = blueprint.image
                createdAt = blueprint.createdAt
            }
        }
    }

    override suspend fun delete(id: Long) {
        return newSuspendedTransaction(db = database) {
            BlueprintEntityImpl.findById(id)?.delete()
        }
    }

}