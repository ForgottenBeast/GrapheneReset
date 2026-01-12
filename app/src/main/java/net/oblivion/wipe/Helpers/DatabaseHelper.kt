package net.oblivion.wipe.Helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "oblivion.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "user_data"
        private const val COLUMN_ID = "id"
        private const val COLUMN_USER_KEY = "user_key"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_KEY TEXT
            )
        """.trimIndent()
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun getOwnCode(): String {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, arrayOf(COLUMN_USER_KEY), null, null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(it.getColumnIndexOrThrow(COLUMN_USER_KEY))
            }
        }

        // If there’s no existing key, generate one and store it
        val newKey = KeyHelper().generateKey()
        val values = ContentValues().apply {
            put(COLUMN_USER_KEY, newKey)
        }
        db.insert(TABLE_NAME, null, values)
        return newKey
    }
}