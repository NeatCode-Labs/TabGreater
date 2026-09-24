package com.neatcode.tabgreater.core.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The exported Room schema is the contract the migrations are written against. There is no
 * instrumented test infrastructure in this project, so the real 1 → 2 upgrade is verified on the
 * emulator (install the previous release over its own data, update, draw, relaunch); this test
 * pins what the JVM can check: the exported v2 schema holds `chart_drawings` with exactly the
 * three columns, every v1 table is unchanged, and [DatabaseMigrations.MIGRATION_1_2] creates the
 * new table with Room's own SQL.
 */
class DatabaseSchemaTest {

    /** Gradle runs Android unit tests with the module directory as the working directory. */
    private val schemaDir = File("schemas/${TabGreaterDatabase::class.java.name}")

    private fun schema(version: Int): JsonObject {
        val file = File(schemaDir, "$version.json")
        assertTrue("missing exported schema ${file.absolutePath}", file.isFile)
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
    }

    private fun JsonObject.entity(table: String): JsonObject? =
        getValue("entities").jsonArray.map { it.jsonObject }
            .firstOrNull { it.getValue("tableName").jsonPrimitive.content == table }

    @Test
    fun `version 2 is exported and holds chart_drawings with its three columns`() {
        val v2 = schema(2)
        assertEquals(2, v2.getValue("version").jsonPrimitive.int)
        val drawings = v2.entity(ChartDrawingEntity.TABLE) ?: error("chart_drawings missing from 2.json")

        val columns = drawings.getValue("fields").jsonArray.map { it.jsonObject }.associate {
            it.getValue("columnName").jsonPrimitive.content to
                (it.getValue("affinity").jsonPrimitive.content to it.getValue("notNull").jsonPrimitive.content)
        }
        assertEquals(
            mapOf(
                "market_key" to ("TEXT" to "true"),
                "drawings" to ("TEXT" to "true"),
                "updated_at" to ("INTEGER" to "true"),
            ),
            columns,
        )
        val primaryKey = drawings.getValue("primaryKey").jsonObject.getValue("columnNames").jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(listOf("market_key"), primaryKey)
    }

    @Test
    fun `the 1 to 2 migration creates the table exactly as Room expects it`() {
        val createSql = schema(2).entity(ChartDrawingEntity.TABLE)!!.getValue("createSql").jsonPrimitive.content
            .replace(TABLE_NAME_PLACEHOLDER, ChartDrawingEntity.TABLE)
        assertEquals(createSql, DatabaseMigrations.CREATE_CHART_DRAWINGS)
        assertEquals(1, DatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, DatabaseMigrations.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `version 1 has no drawings table and every v1 table survives into v2 unchanged`() {
        val v1 = schema(1)
        val v2 = schema(2)
        assertNull(v1.entity(ChartDrawingEntity.TABLE))
        v1.getValue("entities").jsonArray.map { it.jsonObject }.forEach { old ->
            val table = old.getValue("tableName").jsonPrimitive.content
            val new = v2.entity(table) ?: error("$table dropped in v2")
            assertEquals("$table changed between v1 and v2", old.getValue("createSql"), new.getValue("createSql"))
            assertEquals("$table indices changed", old["indices"], new["indices"])
        }
    }

    @Test
    fun `every version step has a migration`() {
        val steps = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals(listOf(1 to 2), steps)
    }

    private companion object {
        const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"
    }
}
