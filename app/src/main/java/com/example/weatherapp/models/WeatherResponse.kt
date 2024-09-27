package com.example.weatherapp.models

import java.io.Serializable

data class WeatherResponse(
    val coordinates : Coordinators,
    val weather : List<Weather>,
    val base : String,
    val main : Main,
    val visibility : Int,
    val wind : Wind,
    val clouds : Clouds,
    val dt : Int,
    val sys : Sys,
    val timezone : Long,
    val id : Int,
    val name : String,
    val cod : Int
) : Serializable
