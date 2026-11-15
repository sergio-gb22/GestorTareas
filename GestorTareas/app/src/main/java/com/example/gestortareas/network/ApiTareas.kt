package com.example.gestortareas.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class TareaRemota(val id: Int, val title: String, val completed: Boolean)

interface ApiTareas {
    @GET("todos")
    suspend fun listarTareas(@Query("userId") usuario: Int = 1): List<TareaRemota>
}

object ServicioRed {
    val api: ApiTareas by lazy {
        Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiTareas::class.java)
    }
}