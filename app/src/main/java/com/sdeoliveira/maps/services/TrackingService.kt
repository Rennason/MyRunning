package com.sdeoliveira.maps.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.LatLng
import com.sdeoliveira.maps.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

typealias PoliLine = MutableList<LatLng>
typealias PoliLines = MutableList<PoliLine>

class TrackingService : LifecycleService() {
    var firstRun = true
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val timeRunSeconds = MutableLiveData<Long>()

    companion object {
        val isTracking = MutableLiveData<Boolean>()
        var pathPoints = MutableLiveData<PoliLines>()
        val timeRunMiliSeconds = MutableLiveData<Long>()
    }

    private fun initValues() {
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        timeRunSeconds.postValue(0L)
        timeRunMiliSeconds.postValue(0L)
    }

    override fun onCreate() {
        super.onCreate()
        initValues()
        fusedLocationProviderClient = FusedLocationProviderClient(this)

        isTracking.observe(this, Observer {
            updateLocationTracking(it)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when(it.action){
                "ACTION_START_OR_RESUME_SERVICE" -> {
                    if(firstRun) {
                        startForegroundService()
                        firstRun = false
                    } else {
                        startTimers()
                        Log.d("String","Resuming service...")
                    }
                }
                "ACTION_PAUSE_SERVICE" -> {
                    pauseService()
                    Log.d("String", "PAUSE service")

                }
                "ACTION_STOP_SERVICE" -> {
                    Log.d("String", "Stop")
                }
                else -> {
                    Log.d("String", "ACTION not found")
                }

            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun pauseService(){
        isTracking.postValue(false)
        isTimerEnabled = false
    }

    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSecondTimestamp = 0L

    private fun startTimers(){
        addEmptyPoliline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true
        CoroutineScope(Dispatchers.Main).launch {
            while(isTracking.value!!)
            {
                lapTime = System.currentTimeMillis() - timeStarted

                timeRunMiliSeconds.postValue(timeRun + lapTime)
                if(timeRunMiliSeconds.value!! >= lastSecondTimestamp + 1000L)
                {
                    timeRunSeconds.postValue(timeRunSeconds.value!! +1)
                    lastSecondTimestamp += 1000L
                }
                delay(50L)
            }
            timeRun += lapTime
        }
    }



    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking : Boolean)
    {
        if(isTracking)
        {
            //if(TrackingUtility.hasLocationPermission(this)){

                val request = LocationRequest().apply{
                    interval = 2000L
                    fastestInterval = 1000L
                    priority = PRIORITY_HIGH_ACCURACY
                }

                fusedLocationProviderClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
           //}

        }
        else
        {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }

    }

    val locationCallback = object  : LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            super.onLocationResult(result)
            if(isTracking.value!!) {
                result?.locations?.let { locations ->
                    for(location in locations){
                        addPathPoint(location)
                        Log.d("String", "LOCATION: ${location.latitude} : ${location.longitude}")
                    }
                }
            }
        }
    }

    private fun addPathPoint(position : Location?){
        position?.let {
            val pos = LatLng(position.latitude, position.longitude)
            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        }
    }

    private fun addEmptyPoliline() = pathPoints.value?.apply {
        add(mutableListOf())
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(mutableListOf(mutableListOf()))

    private fun startForegroundService() {
        startTimers()
        isTracking.postValue(true)

        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        val notificationBuilder = NotificationCompat.Builder(this, "tracking_channel")
            .setAutoCancel(false)
            .setOngoing(true)
            //.setSmallIcon(R.drawable.ic_directions_run_black_24dp)
            .setContentTitle("Running App")
            .setContentText("00:00:00")
            .setContentIntent(getMainActivityPendingIntent())

        startForeground(1, notificationBuilder.build())
    }

    private fun getMainActivityPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).also {
            it.action = "ACTION_SHOW_TRACKING_FRAGMENT"
        },
        0//FLAG_UPDATE_CURRENT
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            "tracking_channel",
            "Tracking",
            IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}