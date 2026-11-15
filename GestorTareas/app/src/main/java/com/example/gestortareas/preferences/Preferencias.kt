package com.example.gestortareas.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "ajustes")
private val SOLO_PENDIENTES_KEY = booleanPreferencesKey("solo_pendientes")

class Preferencias(private val context: Context) {
    val soloPendientes: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SOLO_PENDIENTES_KEY] ?: false
    }

    suspend fun guardarSoloPendientes(valor: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SOLO_PENDIENTES_KEY] = valor
        }
    }
}