package fr.kalori.app.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** Les enums sont gérés nativement par Room (stockés par leur nom). */
class Converters {

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun stringListToJson(list: List<String>): String = json.encodeToString(stringList, list)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = json.decodeFromString(stringList, value)

    private companion object {
        val json = Json
        val stringList = ListSerializer(String.serializer())
    }
}
