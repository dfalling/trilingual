package com.dfalling.trilingual.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(tableName = "translations")
data class TranslationEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val sourceLanguage: String,
    val sourceText: String,
    /** Target language code to translated text, in display order. */
    val translations: Map<String, String>,
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TranslationEntry>>

    @Insert
    suspend fun insert(entry: TranslationEntry): Long

    @Query("DELETE FROM translations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM translations")
    suspend fun clear()
}

/** Stores the translations map as `[[code, text], ...]` so display order survives the round trip. */
object TranslationsConverter {
    @TypeConverter
    @JvmStatic
    fun toJson(map: Map<String, String>): String =
        JSONArray(map.map { (code, text) -> JSONArray(listOf(code, text)) }).toString()

    @TypeConverter
    @JvmStatic
    fun fromJson(json: String): Map<String, String> {
        val array = JSONArray(json)
        return buildMap {
            for (i in 0 until array.length()) {
                val pair = array.getJSONArray(i)
                put(pair.getString(0), pair.getString(1))
            }
        }
    }
}

@Database(entities = [TranslationEntry::class], version = 1)
@TypeConverters(TranslationsConverter::class)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun translations(): TranslationDao

    companion object {
        fun create(context: Context): HistoryDatabase =
            Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db").build()
    }
}
