package com.example.elephant_coller_wildlife // Ensure this matches your AndroidManifest.xml package

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.elephant_coller_wildlife.ui.theme.elephant_coller_wildlifeTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

// Constants for notification and location updates
const val CHANNEL_ID = "elephant_proximity_channel"
const val CHANNEL_NAME = "Elephant Proximity Alerts"
const val CHANNEL_DESCRIPTION = "Notifications when an elephant is nearby."
const val NOTIFICATION_ID = 101


class MainActivity : ComponentActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Activity Result Launcher for requesting location permissions
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        when {
            fineLocationGranted -> {
                Log.d("PERMISSIONS", "Fine location granted")
                // Fine location access granted.
            }
            coarseLocationGranted -> {
                Log.d("PERMISSIONS", "Coarse location granted")
                // Only approximate location access granted.
            }
            else -> {
                Log.d("PERMISSIONS", "No location permissions granted")
                // No location access granted.
            }
        }
    }

    // Activity Result Launcher for requesting POST_NOTIFICATIONS permission (Android 13+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            Log.d("PERMISSIONS", "Notifications permission granted.")
        } else {
            Log.d("PERMISSIONS", "Notifications permission denied.")
            // Optionally, inform the user that notifications won't work
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // For edge-to-edge display

        database = FirebaseDatabase.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Request permissions when the activity is created
        requestLocationPermission()
        requestNotificationPermission() // Request notification permission on app start
        createNotificationChannel() // Create notification channel on app start

        setContent {
            elephant_coller_wildlifeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass the initialized database and fusedLocationClient instances
                    LocationMapScreen(
                        databaseRef = database,
                        fusedLocationClient = fusedLocationClient
                    )
                }
            }
        }
    }

    // Function to request location permissions
    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions are not granted, request them
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Permissions already granted
            Log.d("PERMISSIONS", "Location permissions already granted.")
        }
    }

    // Function to request POST_NOTIFICATIONS permission (Android 13+)
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // TIRAMISU is API 33
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Permission already granted
                Log.d("PERMISSIONS", "Notifications permission already granted.")
            }
        } else {
            // For Android versions below 13, POST_NOTIFICATIONS is not a runtime permission.
            // It's granted automatically if declared in AndroidManifest.xml.
        }
    }

    // Function to create a Notification Channel (required for Android 8.0+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // O is API 26
            val importance = NotificationManager.IMPORTANCE_HIGH // High importance for warnings
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("NOTIFICATION", "Notification Channel Created")
        }
    }
}
const val GEOFENCE_CENTER_LATITUDE = 6.5805
const val GEOFENCE_CENTER_LONGITUDE = 81.397
const val GEOFENCE_RADIUS_METERS = 9000.0


@SuppressLint("DefaultLocale")
@Composable
fun LocationMapScreen(
    databaseRef: DatabaseReference,
    elephantIds: List<String> = listOf("elephantId6"),
    fusedLocationClient: FusedLocationProviderClient
) {
    // State to hold the historical locations for elephantId6
    var elephant6HistoricalLocations by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    // State to hold the latest known location for elephantId6
    var elephant6LatestLocation by remember { mutableStateOf<LatLng?>(null) }
    // State to hold the user's current location
    var userLatestLocation by remember { mutableStateOf<LatLng?>(null) }
    // State to indicate if data is still loading
    var isLoading by remember { mutableStateOf(true) }
    // State to hold any error messages
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // State to track if elephantId6 is inside the geofence
    var isElephant6Inside by remember { mutableStateOf(true) }

    val context = LocalContext.current

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(6.5517, 81.43206), 10f)
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    userLatestLocation = LatLng(location.latitude, location.longitude)
                    Log.d("USER_LOCATION", "User location: Lat=${location.latitude}, Lon=${location.longitude}")
                    // Optionally, update camera to user location
                    // cameraPositionState.position = CameraPosition.fromLatLngZoom(userLatestLocation!!, 15f)
                }
            }
        }
    }

    // LaunchedEffect to check if elephantId6 is outside the geofence
    LaunchedEffect(elephant6LatestLocation) {
        elephant6LatestLocation?.let { latestLocation ->
            val isCurrentlyInside = isLocationInsideGeofence(
                latestLocation.latitude,
                latestLocation.longitude,
                GEOFENCE_CENTER_LATITUDE,
                GEOFENCE_CENTER_LONGITUDE,
                GEOFENCE_RADIUS_METERS
            )

            if (isElephant6Inside && !isCurrentlyInside) {
                // Elephant 6 has exited the geofence
                Log.d("GEOFENCE", "ElephantId6 has exited the geofence!")
                sendNotification(
                    context,
                    "Elephant Out of Area!",
                    "Elephant  has moved outside the defined area."
                )
                isElephant6Inside = false // Update state
            } else if (!isElephant6Inside && isCurrentlyInside) {
                // Elephant 6 has re-entered the geofence
                Log.d("GEOFENCE", "ElephantId6 has re-entered the geofence.")
                isElephant6Inside = true // Update state
            } else if (isCurrentlyInside) {
                isElephant6Inside = true // Ensure state is true if it's inside
            }
        }
    }

    // DisposableEffect to manage Firebase listener for elephantId6
    DisposableEffect(databaseRef, elephantIds) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempHistoricalLocations = mutableListOf<LatLng>()
                var latestTimestamp = 0L
                var latestLocation: LatLng? = null

                for (locationSnapshot in snapshot.children) {
                    val timestamp = locationSnapshot.key?.toLongOrNull()
                    val fetchedLatitude = locationSnapshot.child("latitude").getValue(Double::class.java)
                    val fetchedLongitude = locationSnapshot.child("longitude").getValue(Double::class.java)

                    if (timestamp != null && fetchedLatitude != null && fetchedLongitude != null) {
                        val latLng = LatLng(fetchedLatitude, fetchedLongitude)
                        tempHistoricalLocations.add(latLng)
                        if (timestamp > latestTimestamp) {
                            latestTimestamp = timestamp
                            latestLocation = latLng
                        }
                    }
                }

                elephant6HistoricalLocations = tempHistoricalLocations
                elephant6LatestLocation = latestLocation
                isLoading = false
                errorMessage = null
                Log.d("FIREBASE_MAP", "Fetched data for elephantId6: ${tempHistoricalLocations.size} locations")
            }

            override fun onCancelled(error: DatabaseError) {
                errorMessage = "Failed to load movement data for elephantId6: ${error.message}"
                isLoading = false
                Log.e("FIREBASE_MAP", "Error getting movement data for elephantId6: ${error.message}", error.toException())
            }
        }

        databaseRef.child("elephantMovements").child("elephantId6").addValueEventListener(listener)

        onDispose {
            databaseRef.child("elephantMovements").child("elephantId6").removeEventListener(listener)
        }
    }

    // LaunchedEffect to handle user's current location updates
    LaunchedEffect(fusedLocationClient) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("LOCATION", "Location permissions not granted for FusedLocationProviderClient.")
            return@LaunchedEffect
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

        kotlinx.coroutines.awaitCancellation().also {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("USER_LOCATION", "Location updates removed.")
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading && elephant6LatestLocation == null) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Loading elephant 6 movement data...")
            } else if (errorMessage != null) {
                Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    // Display historical location for elephantId6
                    if (elephant6HistoricalLocations.isNotEmpty()) {
                        Polyline(points = elephant6HistoricalLocations, color = MaterialTheme.colorScheme.primary)
                    }

                    // Display marker for elephantId6's latest location
                    elephant6LatestLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "Elephant 6",
                            snippet = "Lat: ${it.latitude}, Lon: ${it.longitude}"
                        )
                    }

                    // Display marker for user's current location (optional)
                    userLatestLocation?.let {
                        Marker(
                            state = MarkerState(position = it),
                            title = "Your Location",
                            snippet = "Lat: ${it.latitude}, Lon: ${it.longitude}",
                            alpha = 0.7f
                        )
                    }
                }
            }
        }
    }
}


// Helper function to send a notification (moved outside LocationMapScreen)
fun sendNotification(context: Context, title: String, message: String) {
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_elephant_notification) // Ensure this drawable exists
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true) // Dismiss notification when tapped

    with(NotificationManagerCompat.from(context)) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("NOTIFICATION", "POST_NOTIFICATIONS permission not granted. Cannot send notification.")
            return
        }
        notify(NOTIFICATION_ID, builder.build())
        Log.d("NOTIFICATION", "Notification sent: $title - $message")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    elephant_coller_wildlifeTheme {
        Text("Preview of My Elephant Collar App")
    }
}
fun isLocationInsideGeofence(
    latitude: Double,
    longitude: Double,
    centerLat: Double,
    centerLon: Double,
    radius: Double
): Boolean {
    val results = FloatArray(1)
    Location.distanceBetween(latitude, longitude, centerLat, centerLon, results)
    val distanceInMeters = results[0]
    return distanceInMeters <= radius
}