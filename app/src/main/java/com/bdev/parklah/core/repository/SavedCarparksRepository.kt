package com.bdev.parklah.core.repository

import android.content.Context
import com.bdev.parklah.core.model.Carpark
import com.bdev.parklah.core.model.SavedCarparkEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class SavedCarparksRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson     = Gson()
    private val listType = object : TypeToken<List<SavedCarparkEntry>>() {}.type

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<SavedCarparkEntry>> = _entries.asStateFlow()

    fun isSaved(code: String): Boolean = _entries.value.any { it.carparkCode == code }

    fun toggle(carpark: Carpark) {
        if (isSaved(carpark.carparkCode)) {
            persist(_entries.value.filter { it.carparkCode != carpark.carparkCode })
        } else {
            persist(
                _entries.value + SavedCarparkEntry(
                    carparkCode   = carpark.carparkCode,
                    carparkName   = carpark.carparkName,
                    dataSource    = carpark.dataSource,
                    lat           = carpark.lat,
                    lon           = carpark.lon,
                    parkingSystem = carpark.parkingSystem,
                )
            )
        }
    }

    fun remove(code: String) = persist(_entries.value.filter { it.carparkCode != code })

    private fun persist(list: List<SavedCarparkEntry>) {
        _entries.value = list
        prefs.edit { putString(KEY, gson.toJson(list)) }
    }

    private fun load(): List<SavedCarparkEntry> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try { gson.fromJson(json, listType) } catch (_: Exception) { emptyList() }
    }

    companion object {
        private const val PREFS_NAME = "parklah_saved"
        private const val KEY        = "entries"
    }
}
