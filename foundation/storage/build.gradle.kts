import groovy.json.JsonSlurper

plugins {
    id("eleckoi.android.library")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.eleckoi.android.foundation.storage"
    testOptions.unitTests.all {
        it.systemProperty("eleckoi.schemaDirectory", file("schemas").absolutePath)
        it.systemProperty("eleckoi.storageSourceDirectory", file("src/main").absolutePath)
    }
}

ksp {
    arg("room.schemaLocation", file("schemas").absolutePath)
}

dependencies {
    api("androidx.paging:paging-runtime:3.5.0")
    api("androidx.room:room-runtime:2.8.4")
    api("androidx.room:room-ktx:2.8.4")
    api("androidx.room:room-paging:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("org.json:json:20240303")
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
}

// The generated SQL is the platform-neutral business schema contract. Room-only identity metadata
// stays in Room's JSON export; every SQLite client gets the same business tables and views.
val exportSqliteSchema by tasks.registering {
    dependsOn("kspDebugKotlin")
    val roomSchema = file("schemas/com.eleckoi.android.foundation.storage.room.ElecKoiDatabase/1.json")
    val sqlFile = file("schemas/eleckoi-common-schema-v1.sql")
    inputs.file(roomSchema)
    outputs.file(sqlFile)
    doLast {
        val document = JsonSlurper().parse(roomSchema) as Map<*, *>
        val database = document["database"] as Map<*, *>
        val statements = mutableListOf("PRAGMA foreign_keys = ON", "BEGIN TRANSACTION")
        (database["entities"] as List<*>).forEach { value ->
            val entity = value as Map<*, *>
            val name = entity["tableName"] as String
            statements += (entity["createSql"] as String).replace("\${TABLE_NAME}", name)
            (entity["indices"] as? List<*>).orEmpty().forEach { index ->
                statements += ((index as Map<*, *>)["createSql"] as String).replace("\${TABLE_NAME}", name)
            }
        }
        (database["views"] as? List<*>).orEmpty().forEach { value ->
            val view = value as Map<*, *>
            statements += (view["createSql"] as String).replace("\${VIEW_NAME}", view["viewName"] as String)
        }
        statements += "PRAGMA user_version = ${database["version"]}"
        statements += "COMMIT"
        sqlFile.writeText("-- Generated from Room schema; do not edit by hand.\n" +
            statements.joinToString(";\n\n", postfix = ";\n"))
    }
}

tasks.matching { it.name == "testDebugUnitTest" || it.name == "assembleDebug" }.configureEach {
    dependsOn(exportSqliteSchema)
}
