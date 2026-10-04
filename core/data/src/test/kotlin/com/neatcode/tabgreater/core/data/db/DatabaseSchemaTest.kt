package com.neatcode.tabgreater.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * The exported Room schema is the contract the migrations are written against. There is no
 * instrumented test infrastructure in this project, so the real upgrades are verified on the
 * emulator (install the previous release over its own data, update, relaunch); this test pins
 * what the JVM can check: the exported v2 schema holds `chart_drawings` with exactly the three
 * columns, every v1 table is unchanged, and [DatabaseMigrations.MIGRATION_1_2] creates the new
 * table with Room's own SQL; v3 adds exactly `asset_class` and `underlying` to `markets`, and
 * [DatabaseMigrations.MIGRATION_2_3] adds them with the definitions of a fresh install.
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

    private fun JsonObject.tableNames(): List<String> =
        getValue("entities").jsonArray.map { it.jsonObject.getValue("tableName").jsonPrimitive.content }

    private fun JsonObject.createSql(table: String): String =
        getValue("createSql").jsonPrimitive.content.replace(TABLE_NAME_PLACEHOLDER, table)

    /** What Room's schema validation compares per column: affinity, `NOT NULL` and the default. */
    private data class Column(val affinity: String, val notNull: Boolean, val defaultValue: String?)

    private fun JsonObject.columns(): Map<String, Column> =
        getValue("fields").jsonArray.map { it.jsonObject }.associate {
            it.getValue("columnName").jsonPrimitive.content to Column(
                affinity = it.getValue("affinity").jsonPrimitive.content,
                notNull = it["notNull"]?.jsonPrimitive?.boolean ?: false,
                defaultValue = it["defaultValue"]?.jsonPrimitive?.content,
            )
        }

    /** Runs [migration] against a stand-in database that only records the SQL it executes. */
    private fun executedSql(migration: Migration): List<String> {
        val executed = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            check(method.name == "execSQL" && args?.size == 1) { "unexpected call ${method.name}" }
            executed += args[0] as String
            null
        } as SupportSQLiteDatabase
        migration.migrate(db)
        return executed
    }

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
    fun `version 3 adds the asset class and the underlying to markets and nothing else`() {
        val v2 = schema(2).entity(MARKETS) ?: error("markets missing from 2.json")
        val v3 = schema(3).entity(MARKETS) ?: error("markets missing from 3.json")
        assertEquals(3, schema(3).getValue("version").jsonPrimitive.int)

        val columns = v3.columns()
        assertEquals(Column("TEXT", notNull = true, defaultValue = "'crypto'"), columns["asset_class"])
        assertEquals(Column("TEXT", notNull = false, defaultValue = null), columns["underlying"])
        assertEquals(v2.columns(), columns - "asset_class" - "underlying")
        assertEquals("markets indices changed", v2["indices"], v3["indices"])
        assertEquals(v2["primaryKey"], v3["primaryKey"])
    }

    @Test
    fun `the 2 to 3 migration adds the columns exactly as Room declares them`() {
        // Room creates a fresh v3 table with the two definitions appended to the v2 ones, so the
        // ALTERs below leave an upgraded table identical to it, the 'crypto' default included,
        // which Room's validation compares because the entity declares it.
        val appended = ", ${DatabaseMigrations.MARKETS_ASSET_CLASS_COLUMN}, ${DatabaseMigrations.MARKETS_UNDERLYING_COLUMN}"
        val v2 = schema(2).entity(MARKETS)!!.createSql(MARKETS)
        assertEquals(
            v2.replace(", PRIMARY KEY(", "$appended, PRIMARY KEY("),
            schema(3).entity(MARKETS)!!.createSql(MARKETS),
        )
        assertEquals(
            listOf(
                "ALTER TABLE markets ADD COLUMN ${DatabaseMigrations.MARKETS_ASSET_CLASS_COLUMN}",
                "ALTER TABLE markets ADD COLUMN ${DatabaseMigrations.MARKETS_UNDERLYING_COLUMN}",
                // Re-opens the 24 h freshness gate, so the catalogue is classified on the next refresh.
                "UPDATE markets SET updated_at = 0",
            ),
            executedSql(DatabaseMigrations.MIGRATION_2_3),
        )
        assertEquals(2, DatabaseMigrations.MIGRATION_2_3.startVersion)
        assertEquals(3, DatabaseMigrations.MIGRATION_2_3.endVersion)
    }

    @Test
    fun `every other v2 table survives into v3 unchanged`() {
        val v2 = schema(2)
        val v3 = schema(3)
        val tables = v2.tableNames()
        assertEquals(tables, v3.tableNames())
        for (table in tables - MARKETS) {
            assertEquals("$table changed between v2 and v3", v2.entity(table), v3.entity(table))
        }
    }

    @Test
    fun `every version step has a migration`() {
        val steps = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals(listOf(1 to 2, 2 to 3), steps)
        // The newest exported schema is the last migration's target: a version bump without a
        // migration exports a schema this list does not reach.
        val exported = schemaDir.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()
        assertEquals(exported.zipWithNext(), steps)
    }

    private companion object {
        const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"
        const val MARKETS = "markets"
    }
}
