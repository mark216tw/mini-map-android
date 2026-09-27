package com.example.minimap

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "places", indices = [Index(value = ["latitude", "longitude"], unique = true)])
data class Place(
    @PrimaryKey val id: String,
    val name: String,
    val note: String,
    val latitude: Double,
    val longitude: Double
)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Place>>

    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Place>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(place: Place): Long

    @Query("UPDATE OR IGNORE places SET name = :name, note = :note WHERE id = :id")
    suspend fun updateDetails(id: String, name: String, note: String): Int

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [Place::class], version = 1, exportSchema = false)
abstract class PlacesDatabase : RoomDatabase() {
    abstract fun places(): PlaceDao
}
