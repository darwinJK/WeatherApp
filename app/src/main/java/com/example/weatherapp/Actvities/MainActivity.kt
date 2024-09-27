package com.example.weatherapp.Actvities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.weatherapp.Constants.Constants
import com.example.weatherapp.Constants.Constants.WEATHER_RESPONSE_DATA
import com.example.weatherapp.Network.WeatherService
import com.example.weatherapp.R
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var customDialog : Dialog? = null
    private var binding : ActivityMainBinding? = null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)


        mSharedPreferences = getSharedPreferences(
            Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        settingUI()

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            if(!isLocationEnabled()){
                Toast.makeText(this,"Provider is turned off. Please turn it on",
                    Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }else{
                askPermission()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
        else -> super.onOptionsItemSelected(item)
    }
    }


    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        var mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority=LocationRequest.QUALITY_BALANCED_POWER_ACCURACY

        if(Constants.isNetworkAvailable(this)){
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,mLocationCallBack, Looper.myLooper())
        }else{
            Toast.makeText(this@MainActivity,"No internet connection",
                Toast.LENGTH_SHORT).show()
        }
    }

    private var mLocationCallBack = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val mLastLocation : Location? = locationResult.lastLocation
            val mLatitude = mLastLocation!!.latitude
            val mLongitude = mLastLocation.longitude
            getLocationWeatherDetails(mLatitude,mLongitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        showProgressDialog()
        if(Constants.isNetworkAvailable(this)){
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit.create(WeatherService::class.java)

            val listCall : Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )

            listCall.enqueue(object :Callback<WeatherResponse>{
                override fun onResponse(call : Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    if(response.isSuccessful){
                        dismissProgressDialog()

                        val weatherList : WeatherResponse? = response.body()
                        val weatherResponseJsonString =Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()
                        settingUI()

                    }else{
                        dismissProgressDialog()
                        val responseCode = response.code()
                        when(responseCode){
                            400->{
                                Toast.makeText(this@MainActivity,"bad connection",Toast.LENGTH_SHORT).show()

                            }
                            404 -> {
                                Toast.makeText(this@MainActivity,"not found",Toast.LENGTH_SHORT).show()
                            }

                            else -> Toast.makeText(this@MainActivity,"generic error",Toast.LENGTH_SHORT).show()

                        }

                    }
                }

                override fun onFailure(p0: Call<WeatherResponse>,t: Throwable) {
                    Toast.makeText(this@MainActivity,"error occurred ${t.message.toString()}",Toast.LENGTH_SHORT).show()
                    Log.e("error", "error occurred ${t.message.toString()}")
                    dismissProgressDialog()
                }

            })

        }else{
            dismissProgressDialog()
            Toast.makeText(this@MainActivity,"No internet connection",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun askPermission(){
        Dexter.withContext(this).withPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION).withListener(object : MultiplePermissionsListener{
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                if(report!!.areAllPermissionsGranted()){
                    requestLocationData()
                }
            }
            override fun onPermissionRationaleShouldBeShown(
                p0: MutableList<PermissionRequest>?,
                p1: PermissionToken?
            ) {
                showRationalDialog()
            }

        }).onSameThread().check()
    }

    private fun showRationalDialog() {
       val dialog =  AlertDialog.Builder(this)
        dialog.setMessage("This means you have denied the permissions. Please turn on the permission to continue.")
        dialog.setPositiveButton("Go to settings"){
            _,_ ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package",packageName,null) // used for particular app so this app uri is used/
                intent.data = uri
                startActivity(intent)
            }catch (e:Exception){
                e.printStackTrace()
            }

        }
        dialog.setNegativeButton("Cancel"){
            dialog,_->
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun isLocationEnabled():Boolean{
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showProgressDialog(){
        customDialog = Dialog(this)
        customDialog!!.setContentView(R.layout.custom_progress_dialog)
        customDialog!!.setCancelable(false)
        customDialog!!.show()
        Log.d("tet","customdialog : $customDialog")
    }
    private fun dismissProgressDialog(){
        if(customDialog!=null){
            customDialog!!.dismiss()
            customDialog = null
        }
    }

    private fun settingUI(){


        val weatherResponseJsonString = mSharedPreferences.getString(WEATHER_RESPONSE_DATA,"")
        if(!weatherResponseJsonString.isNullOrEmpty()){
                val weatherResponse = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)
            for (i in weatherResponse.weather.indices){
                Log.i("weather Name",weatherResponse.weather.toString())
                binding?.tvWeather?.text = weatherResponse.weather[i].main
                binding?.tvWeatherCon?.text = weatherResponse.weather[i].description
                binding?.tvTempMaximum?.text = "max:"+ weatherResponse.main.temp_max.toString() +
                        getUnit(application.resources.configuration.locales.toString())
                binding?.tvTempMinimum?.text = "min:"+weatherResponse.main.temp_min.toString() +
                        getUnit(application.resources.configuration.locales.toString())
                binding?.tvSunrise?.text=unixTime(weatherResponse.sys.sunrise)
                binding?.tvSunset?.text=unixTime(weatherResponse.sys.sunset)
                binding?.tvWind?.text = weatherResponse.wind.speed.toString()
                binding?.tvLocationName?.text = weatherResponse.name
                binding?.tvLocationCountry?.text = weatherResponse.sys.country
                binding?.tvDegreePer?.text=weatherResponse.main.humidity.toString()+"%"

                binding?.tvDegree?.text = weatherResponse.main.temp_min.toString() +
                        getUnit(application.resources.configuration.locales.toString())

                iconChanger(weatherResponse,i)
            }
        }


    }

    private fun iconChanger(weatherResponse: WeatherResponse, i: Int) {
        when(weatherResponse.weather[i].icon){
            "01d" -> binding?.imageWeather?.setImageResource(R.drawable.clearskyd)
            "02d" -> binding?.imageWeather?.setImageResource(R.drawable.fewcloudsd)
            "03d" -> binding?.imageWeather?.setImageResource(R.drawable.scatteredcloudsd)
            "04d" -> binding?.imageWeather?.setImageResource(R.drawable.brokencloudsd)
            "09d" -> binding?.imageWeather?.setImageResource(R.drawable.showerraind)
            "10d" -> binding?.imageWeather?.setImageResource(R.drawable.rain)
            "11d" -> binding?.imageWeather?.setImageResource(R.drawable.thunderstormd)
            "13d" -> binding?.imageWeather?.setImageResource(R.drawable.snowflake)
            "15d" -> binding?.imageWeather?.setImageResource(R.drawable.mistd)

            "01n" -> binding?.imageWeather?.setImageResource(R.drawable.clearskyn)
            "02n" -> binding?.imageWeather?.setImageResource(R.drawable.fewcloudsn)
            "09n" -> binding?.imageWeather?.setImageResource(R.drawable.showerraind)
            "03n" -> binding?.imageWeather?.setImageResource(R.drawable.scatteredcloudsd)
            "04n" -> binding?.imageWeather?.setImageResource(R.drawable.brokencloudsd)
            "10n" -> binding?.imageWeather?.setImageResource(R.drawable.rain)
            "11n" -> binding?.imageWeather?.setImageResource(R.drawable.thunderstormd)
            "13n" -> binding?.imageWeather?.setImageResource(R.drawable.snowflake)
            "15n" -> binding?.imageWeather?.setImageResource(R.drawable.mistd)
        }
    }

    private fun getUnit(value:String):String?{
        var value = "°C"
        if("US"==value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex:Long) : String?{
        val date = Date(timex*1000)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}