package com.example.fitsync.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng

class FitnessRepository(private val context: Context) {
    private var cumulativeCalories : Float = 0f
    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastValidation : Location? = null
    private var isUserActuallyMoving = false
    private var totalDuration:Long =0
    private var trackingStartTime:Long =0
    private var movementThreshold = 3.0f
    private val _routePoints = MutableLiveData<List<LatLng>>(emptyList())
     val routePoints:LiveData<List<LatLng>> =_routePoints
    private val _totalDistance = MutableLiveData(0f)
     val totalDistance:LiveData<Float> = _totalDistance
    private val _isTracking = MutableLiveData(false)
     val isTracking:LiveData<Boolean> = _isTracking
    private val _currentLocation = MutableLiveData<LatLng>(null)
    val currentLocation: LiveData<LatLng> = _currentLocation
    private var startTime: Long= 0
    private var activeMovement : Long =0
    private var lastMovementTime: Long = 0
    private var lastLocationUpdateTime : Long =0

    private val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    newLocation ->
                    Log.d("FitnessRepository", "new location Received: ${newLocation.latitude},${newLocation.longitude}")
                    val currentTime :Long = System.currentTimeMillis()
                    _currentLocation.postValue(LatLng(newLocation.latitude,newLocation.longitude))

                    if (newLocation.accuracy > 20f){
                        Log.d("FitnessRepository", "Location Accuracy too poor")
                        isUserActuallyMoving = false
                        return
                    }
                    lastValidation?.let { lastLocation ->
                        val distance: Float = newLocation.distanceTo(lastLocation)
                        val timeGap = currentTime - lastLocationUpdateTime
                        val speed = if (timeGap > 0) (distance * 1000) / timeGap else 0f

                        isUserActuallyMoving = distance > movementThreshold &&
                                speed < 8f &&
                                speed > 0.5f
                        if (isUserActuallyMoving){
                            updateMovementTime(currentTime)
                            updateLocationData(newLocation)
                            Log.d("FitnessRepository", "Valid movement: $distance metres, Speed: $speed m/s")
                        }
                    }?.run { 
                        lastValidation = newLocation
                        isUserActuallyMoving =false
                    }
                    lastLocationUpdateTime = currentTime
                }
            }

    }


    private fun updateMovementTime(currentTime: Long) {
        if (lastMovementTime> 0){
            activeMovement += currentTime - lastMovementTime
        }
        lastMovementTime = currentTime
    }


    fun getCurrentDuration(): Long {
        return  if (_isTracking.value == true){
            System.currentTimeMillis() -trackingStartTime
        }else{
            totalDuration
        }
    }

    private fun updateLocationData(newLocation: Location) {
                if(!isUserActuallyMoving){
                    Log.d("FitnessRepository", "User not actually moving")
                    return
                }
                val  latling = LatLng(newLocation.latitude,newLocation.longitude)
                val currentPoints = _routePoints.value?.toMutableList()?: mutableListOf()
                    if (currentPoints.isEmpty()){
                        currentPoints.add(latling)
                        _routePoints.postValue(currentPoints)
                        Log.d("FitnessRepository", "First location added")
                        return
                    }
                val distanceFromLastPoint : Float = calculateDistance(currentPoints.last(),latling)
                if (distanceFromLastPoint>movementThreshold/100f){
                    currentPoints.add(latling)
                    _routePoints.postValue(currentPoints)
                    val currentTotal = _totalDistance.value?:0f
                    val newTotal = currentTotal + distanceFromLastPoint
                    _totalDistance.postValue(newTotal)
//                    Log.d("FitnessRepository", "New location added")
//                    return
                }
        lastValidation = newLocation
    }

    private fun intializeTracking(){
        startTime = System.currentTimeMillis()
        trackingStartTime= System.currentTimeMillis()
        _isTracking.postValue(true)
    }

    private fun  requestLocationUpdates(){
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 5000
            fastestInterval = 2000
            smallestDisplacement = 1f
        }
        if(checkLocationPermission()){
            try {
                locationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    context.mainLooper
                )
            }catch (e:SecurityException){
                Toast.makeText(context,"Location permission not granted",Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun calculateDistance(last: LatLng, latlng: LatLng): Float {
    val result = FloatArray(1)
        Location.distanceBetween(
            last.latitude,last.longitude,
            latlng.latitude,latlng.longitude,
            result
        )
        return result[0]/1000f
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )== PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )== PackageManager.PERMISSION_GRANTED
    }

     fun calculateCaloriesBurned(weight : Float,distance:Float,duration: Long):Float{
        if (duration< 1000){
            return cumulativeCalories
        }
        val hours = duration/(1000.0 * 60.0 *60.0)
        if (hours <=0) return cumulativeCalories

        val speed = if (hours>0) distance/hours else 0.0
        val met= when{
            speed<= 4.0 ->2.0
            speed<=8.0 ->7.0
            speed<=11.0 ->8.5
            speed<=14.0 ->10.5
            else -> 10.0
        }.toFloat()
         if (isUserActuallyMoving){
             cumulativeCalories = (met * weight * hours).toFloat()
             Log.d("FitnessRepository", "Calories Burned: $cumulativeCalories (MET: $met, Speed: $speed km/h )")
         }
         return cumulativeCalories

    }

     fun calculateAveragePace(distance: Float, duration: Long): Double {
        if (duration < 1000 || distance <= 0) {
            return 0.0
        }
        val hours = duration / (1000.0 * 60.0 * 60.0)
        return distance/hours
    }

     fun startTracking(){
        if(checkLocationPermission()){
            intializeTracking()
            requestLocationUpdates()
        }
    }

    fun stopTracking(){
        _isTracking.postValue(false)
        locationClient.removeLocationUpdates(locationCallback)
        totalDuration = System.currentTimeMillis() - startTime
        resetTimers()
    }

    private fun resetTimers() {
        lastMovementTime =0
        activeMovement=0
        trackingStartTime=0
        totalDuration=0
    }
   fun clearTracking(){
       _routePoints.postValue(emptyList())
       _totalDistance.postValue(0f)
       _isTracking.postValue(false)
       startTime =0
       resetTimers()
       lastValidation = null
       isUserActuallyMoving =false
       cumulativeCalories
   }
}