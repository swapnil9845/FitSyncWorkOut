package com.example.fitsync.ui.ViewModel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fitsync.data.repository.FitnessRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FitnessViewModel(private val repository: FitnessRepository,
                       private val context: Context)
    : ViewModel() {

        private val _userWeight = MutableLiveData(70f)
        val currentLocation:LiveData<LatLng> = repository.currentLocation
    private val _distance = MutableLiveData(0f)
    val distance : LiveData<Float> = _distance
    private val _calories = MutableLiveData(0f)
    val calories :LiveData<Float> = _calories
    private val _isTracking =MutableLiveData(false)
    val isTracking : LiveData<Boolean> = _isTracking
    private val _duration = MutableLiveData(0L)
    val duration : LiveData<Long> = _duration
    private val _pace = MutableLiveData(0.0)
    val pace : LiveData<Double> = _pace

    val routePoints : LiveData<List<LatLng>> = repository.routePoints
    private var durationUpdateJob :Job? = null
    private var activeTime : Long =0
    private var lastUpdatedTime : Long =0

    init {
        repository.totalDistance.observeForever{
            newDistance ->
              _distance.value=newDistance
            updateCalories()
            updatePace()

        }
        repository.isTracking.observeForever {
            isTracking ->
             _isTracking.value =isTracking
            if (!isTracking){
                lastUpdatedTime =0
            }
        }
    }


     fun startWorkOut(){
        viewModelScope.launch {
            _duration.value =0
            _calories.value =0f
            _distance.value = 0f
            activeTime = 0
            lastUpdatedTime = System.currentTimeMillis()
            _isTracking.value = true
            repository.startTracking()
            startDurationUpdate()

        }
    }

    private fun startDurationUpdate() {
    durationUpdateJob?.cancel()
        durationUpdateJob =viewModelScope.launch {
            while (isActive && _isTracking.value == true){
                _duration.value =repository.getCurrentDuration()
                updateCalories()
                delay(1000)
            }
        }
    }

    private fun updateCalories() {
       val weight = _userWeight.value?:70f
        val distance = _distance.value?:0f
        val duration = _duration.value?:0L
        val newCalories :Float = repository.calculateCaloriesBurned(weight=weight,distance=distance,duration=duration)
        _calories.value = newCalories
    }


    private fun updatePace() {
        val distance = _distance.value?:0f
        val duration = _duration.value?:0L
        if (distance>0){
            _pace.value = repository.calculateAveragePace(distance = distance,duration = duration)
        }

    }

    fun stopWorkOut(){
        _isTracking.value = false
        repository.stopTracking()
        stopDurationUpdate()
        lastUpdatedTime =0
        activeTime =0
//        saveWorkoutSession()
    }
    fun pauseWorkOut(){
        _isTracking.value = false
        repository.stopTracking()
        stopDurationUpdate()
        lastUpdatedTime=0

    }
    fun resumeWorkOut(){
        viewModelScope.launch {
            lastUpdatedTime = System.currentTimeMillis()
            _isTracking.value =true
            repository.startTracking()
            startDurationUpdate()
        }
    }
    fun clearWorkOut(){
        _distance.value =0f
        _calories.value =0f
        _duration.value =0L
        _pace.value =0.0
        repository.clearTracking()
        activeTime =0
        lastUpdatedTime=0
    }

    fun formatDuration(duration: Long):String{
        val seconds: Long = (duration/1000)%60
        val minutes: Long = (duration/(1000*60))%60
        val hours: Long = duration/(1000*60*60)
        return String.format("%02d:%02d:%02d",hours,minutes,seconds)
    }

    fun formatPace(pace:Double):String{
        return String.format("%.2f min/km",pace)
    }
    fun formatCalories(calories: Float):String{
        return String.format("%.2f kcal",calories)
    }
    fun formatDistance(distance: Float):String{
        return String.format("%.2f km",distance)
    }

    private fun stopDurationUpdate(){
        durationUpdateJob?.cancel()
        durationUpdateJob =null
    }

    fun updateWeight(weight:Float){
         _userWeight.value = weight
        updateCalories()
    }

    class Factory(private val repository: FitnessRepository, private val context: Context) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass:Class<T>):T{
            if(modelClass.isAssignableFrom(FitnessViewModel::class.java)){
                @Suppress("UNCHECKED_CAST")
                return FitnessViewModel(repository,context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}