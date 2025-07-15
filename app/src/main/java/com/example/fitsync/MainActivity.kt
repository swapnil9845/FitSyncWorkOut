package com.example.fitsync

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.fitsync.data.repository.FitnessRepository
import com.example.fitsync.databinding.ActivityMainBinding
import com.example.fitsync.ui.ViewModel.FitnessViewModel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {
    private lateinit var map:MapView
    private lateinit var currentLocationMarker : Marker
    private lateinit var startStopButton : Button
    private lateinit var distanceValue : TextView
    private lateinit var caloriesValue : TextView
    private lateinit var pathPolyLine : Polyline
    private var firstLocationUpdate = true
    private lateinit var durationValue : TextView
    private var isWorkOutPaused = false

    private val viewModel: FitnessViewModel by viewModels {
        FitnessViewModel.Factory(FitnessRepository(this),context = this)
    }

    private val requestPermissionLauncher = registerForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ){
        permissions ->
        if(permissions.all{it.value}){
            viewModel.startWorkOut()
        }
    }

    private lateinit var binding : ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this,getPreferences(MODE_PRIVATE))

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        map = binding.map
        currentLocationMarker = Marker(map)
        startStopButton =binding.actionButton
        distanceValue = binding.distanceValue
        caloriesValue = binding.calorieValue
        pathPolyLine = Polyline(map)
        durationValue = binding.durationValue
        setUpMap()
        setUpObservers()
        setUpButtons()
        setUpWeightInput()
    }
    private fun setUpButtons() {
      binding.actionButton.setOnClickListener{
          if (!viewModel.isTracking.value!!) {
              val weight =binding.weightInput.text.toString().toFloatOrNull()
              if (weight == null || weight <=40){
                  binding.weightInput.error = "Please Enter a Valid Weight"
                  return@setOnClickListener
              }
              checkPermissionAndStartTracking()
              binding.pauseResumeButton.visibility = View.VISIBLE
          }else{
              stopTracking()
              binding.pauseResumeButton.visibility = View.GONE
              viewModel.clearWorkOut()

          }
      }

      binding.pauseResumeButton.setOnClickListener{
          if (isWorkOutPaused){
              resumeTracking()
          }else{
              pauseTracking()
          }
      }


    }

    private fun setUpWeightInput() {
        binding.weightInput.setOnEditorActionListener{_,_,_ ->
            val weight = binding.weightInput.text.toString().toFloatOrNull()
            if (weight != null && weight > 0) {
                viewModel.updateWeight(weight)
            }
            false
        }
    }



    private fun pauseTracking(){
        isWorkOutPaused = true
        binding.pauseResumeButton.text = getString(R.string.Resume)
        viewModel.pauseWorkOut()
    }

    private fun resumeTracking(){
        isWorkOutPaused = false
        binding.pauseResumeButton.text = getString(R.string.pause)
        viewModel.resumeWorkOut()
    }
    private fun startTracking(){
        viewModel.startWorkOut()
    }
    private fun stopTracking(){
        viewModel.stopWorkOut()
    }





    private fun setUpMap() {
       map.apply {
           setTileSource(TileSourceFactory.MAPNIK)
           setMultiTouchControls(true)
           controller.setZoom(15.0)
       }
        currentLocationMarker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM)
            icon= ContextCompat.getDrawable(this@MainActivity, org.osmdroid.wms.R.drawable.osm_ic_follow_me_on)
            title = "current location"
        }
        map.overlays.add(currentLocationMarker)
        pathPolyLine = Polyline(map).apply {
            outlinePaint.color = ContextCompat.getColor(this@MainActivity,android.R.color.holo_blue_dark)
            outlinePaint.strokeWidth= 10f
        }
        map.overlays.add(pathPolyLine)
        startLocationUpdates()
    }

    private fun checkLocationPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermission()) return

        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000
            fastestInterval = 500
        }
        val locationCallback = object: LocationCallback(){
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    location ->
                    val latling = LatLng(location.latitude,location.longitude)
                    updateLocationMarker(latling)
                }
            }
        }
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(locationRequest,locationCallback,mainLooper)
        }catch (e:SecurityException){
            Toast.makeText(this,"Unable to fetch Location this time",Toast.LENGTH_LONG).show()
        }
    }

    private fun updateLocationMarker(latling: LatLng) {
            val geoPoint = GeoPoint(latling.latitude,latling.longitude)
            currentLocationMarker.position = geoPoint
            if (firstLocationUpdate){
                map.controller.setZoom(20.0)
                map.controller.setCenter(geoPoint)
                map.controller.animateTo(geoPoint)
                firstLocationUpdate = false
            }else if(viewModel.isTracking.value == true){
                map.controller.animateTo(geoPoint)
            }
        map.invalidate()
    }

    private fun setUpObservers() {
        viewModel.isTracking.observe(this){
            isTracking ->
            binding.actionButton.text = when{
                isWorkOutPaused -> "STOP"
                !isTracking -> "START"
                else -> "STOP"
            }
        }
        viewModel.duration.observe(this){
            duration ->
            durationValue.text = viewModel.formatDuration(duration)
        }

        viewModel.routePoints.observe(this){
            points ->
            if (points.isNotEmpty()){
                updateRouteOnMap(points)
            }
        }
        viewModel.currentLocation.observe(this){location->
            if (location != null) {
                updateLocationMarker(location)
                Log.d(
                    "MainActivity",
                    "Current Location: ${location.latitude},${location.longitude}"
                )
            }
        }
        viewModel.distance.observe(this){
            distance ->
            Log.d("MainActivity", "Distance Updated: $distance")
            distanceValue.text = viewModel.formatDistance(distance)
        }
        viewModel.calories.observe(this){
            calories ->
            caloriesValue.text = viewModel.formatCalories(calories)
            Log.d("MainActivity", "Calories Updated: $calories")
        }
    }
    private fun updateRouteOnMap(points: List<LatLng>){
     if (points.isEmpty()) return

     try {
       val geoPoints = points.map {GeoPoint(it.latitude,it.longitude)}
       val currentLocation =geoPoints.last()

       updateLocationMarker(LatLng(currentLocation.latitude,currentLocation.longitude))
       pathPolyLine.setPoints(geoPoints)

       if (viewModel.isTracking.value == true){
           map.controller.animateTo(currentLocation)
       }
         map.invalidate()

         Log.d("MainActivity","Updated location: ${currentLocation.latitude},${currentLocation.longitude}")
     } catch (e:Exception){
         Log.e("MainActivity","Error updating route on map",e)
     }
    }

    private fun checkPermissionAndStartTracking(){
        if(checkLocationPermission()){
            startTracking()
        }else{
            requestPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopWorkOut()
    }
}