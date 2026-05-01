package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Ubuntu sunucu IP adresini buraya girin (Örn: "http://192.168.1.100:8000/")
    private const val BASE_URL = "http://10.58.65.31:8000/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
