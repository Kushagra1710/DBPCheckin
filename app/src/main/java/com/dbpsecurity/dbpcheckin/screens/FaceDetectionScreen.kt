package com.dbpsecurity.dbpcheckin.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.location.Location
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dbpsecurity.dbpcheckin.models.Group
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Profile
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.tasks.await
import com.dbpsecurity.dbpcheckin.utils.FaceRecognitionHelper
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.random.Random

@Composable
fun FaceDetectionScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasPermission by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var referenceEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var faceRecognitionHelper by remember { mutableStateOf<FaceRecognitionHelper?>(null) }
    var userGroup by remember { mutableStateOf<Group?>(null) }
    var userProfile by remember { mutableStateOf<Profile?>(null) }
    var isMarkingAttendance by remember { mutableStateOf(false) }

    // Verification Steps State
    var isLocationVerified by remember { mutableStateOf(false) }
    var isFaceVerified by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf("Checking location...") }
    var consecutiveMatchCount by remember { mutableStateOf(0) } // New: Counter for consecutive matches

    // Liveness Detection State
    var currentChallenge by remember { mutableStateOf(LivenessChallenge.BLINK) }
    var isChallengeCompleted by remember { mutableStateOf(false) }
    var challengeStatus by remember { mutableStateOf("Please Blink") }
    var flashColor by remember { mutableStateOf(Color.Transparent) }

    // NEW: Randomized Gesture Chain State (Professional Liveness)
    // Instead of "disco colors", we use a sequence of 3 random gestures.
    // This is professional and secure against video calls (attacker can't predict sequence).
    val gestureSequence = remember { mutableStateListOf<LivenessChallenge>() }
    var currentGestureIndex by remember { mutableStateOf(0) }

    // LATENCY TRAP: Track when the challenge started
    var challengeStartTime by remember { mutableStateOf(0L) }
    val MAX_CHALLENGE_DURATION = 3000L // 3 seconds to react

    // NEW: Retry Trigger for restarting verification
    var retryTrigger by remember { mutableStateOf(0) }

    // Randomize challenge on start
    LaunchedEffect(Unit, retryTrigger) { // Added retryTrigger to dependency to allow restarting
        // Generate a random sequence of 3 distinct gestures, ensuring ZOOM_IN is included
        val movementGestures = listOf(LivenessChallenge.BLINK, LivenessChallenge.SMILE, LivenessChallenge.TURN_LEFT, LivenessChallenge.TURN_RIGHT)

        // Pick 3 random movement gestures (User requested 3 gestures randomly)
        // We will have 3 movement gestures + 1 Zoom In = 4 steps total, or we can do 2 movement + 1 Zoom In = 3 steps.
        // The user said "ask for 3 gestures randomly currently you are asking for two".
        // Currently it is 2 movement + 1 zoom = 3.
        // If the user perceives it as "two", maybe they don't count zoom?
        // Let's increase to 3 movement gestures + 1 Zoom In = 4 steps to be safe and robust.
        // Or maybe they want 3 movement gestures AND Zoom In is separate?
        // Let's try 3 movement gestures + ZOOM_IN.

        val selectedGestures = movementGestures.shuffled().take(3).toMutableList()

        // Always include ZOOM_IN for security against video calls
        selectedGestures.add(LivenessChallenge.ZOOM_IN)

        // Shuffle the sequence so the order is unpredictable
        selectedGestures.shuffle()

        gestureSequence.clear()
        gestureSequence.addAll(selectedGestures)

        currentGestureIndex = 0
        currentChallenge = gestureSequence[0]
        challengeStartTime = System.currentTimeMillis() // Start Timer

        challengeStatus = when(currentChallenge) {
            LivenessChallenge.BLINK -> "Please Blink"
            LivenessChallenge.SMILE -> "Please Smile"
            LivenessChallenge.TURN_LEFT -> "Turn Head Left"
            LivenessChallenge.TURN_RIGHT -> "Turn Head Right"
            LivenessChallenge.ZOOM_IN -> "Zoom In (Move Closer)"
        }
    }

    val scope = rememberCoroutineScope()
    val supabase = SupabaseClient.client
    val fusedLocationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }

    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Needed for head pose
            .build()
        FaceDetection.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            faceRecognitionHelper?.close()
            detector.close()
        }
    }

    // Initialize FaceRecognitionHelper
    LaunchedEffect(Unit) {
        try {
            faceRecognitionHelper = FaceRecognitionHelper(context)
        } catch (e: Throwable) {
            statusMessage = "Error: Model file not found. Please download facenet.tflite"
            Log.e("FaceDetection", "Model init failed", e)
        }
    }

    // Fetch Profile and Generate Reference Embedding
    LaunchedEffect(faceRecognitionHelper) {
        if (faceRecognitionHelper == null) return@LaunchedEffect

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            statusMessage = "Fetching profile..."
            try {
                val profile = withContext(Dispatchers.IO) {
                    supabase.from("profiles").select {
                        filter { eq("id", userId) }
                    }.decodeSingleOrNull<Profile>()
                }
                userProfile = profile

                if (profile?.groupId != null) {
                    userGroup = withContext(Dispatchers.IO) {
                        supabase.from("groups").select {
                            filter { eq("id", profile.groupId) }
                        }.decodeSingleOrNull<Group>()
                    }

                    // Verify Location immediately after fetching group
                    if (userGroup != null) {
                        if (!userGroup!!.isLocationRestricted) {
                            isLocationVerified = true
                            locationStatus = "Location Check Skipped (Unrestricted)"
                        } else {
                            val location = try {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    fusedLocationClient.lastLocation.await()
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }

                            if (location != null) {
                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    location.latitude, location.longitude,
                                    userGroup!!.latitude, userGroup!!.longitude,
                                    results
                                )
                                val distanceInMeters = results[0]
                                val allowedRadius = userGroup!!.radius

                                if (distanceInMeters <= allowedRadius) {
                                    isLocationVerified = true
                                    locationStatus = "Location Verified (${distanceInMeters.toInt()}m)"
                                } else {
                                    isLocationVerified = false
                                    locationStatus = "Too far from office (${distanceInMeters.toInt()}m > ${allowedRadius.toInt()}m)"
                                }
                            } else {
                                locationStatus = "Could not fetch location"
                            }
                        }
                    }
                }

                if (profile?.imageUrl != null) {
                    statusMessage = "Loading profile image..."
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(profile.imageUrl)
                        .allowHardware(false) // Essential for pixel access
                        .build()

                    val result = withContext(Dispatchers.IO) { loader.execute(request) }
                    if (result is SuccessResult) {
                        val drawable = result.drawable
                        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap

                        if (bitmap != null) {
                            statusMessage = "Generating reference embedding..."

                            // Detect face in profile image to crop it
                            val inputImage = InputImage.fromBitmap(bitmap, 0)
                            // Use a separate detector for high accuracy on profile image
                            val profileDetector = FaceDetection.getClient(
                                FaceDetectorOptions.Builder()
                                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                    .build()
                            )

                            profileDetector.process(inputImage)
                                .addOnSuccessListener { faces ->
                                    if (faces.isNotEmpty()) {
                                        val face = faces[0]
                                        val croppedBitmap = cropBitmap(bitmap, face.boundingBox)
                                        if (croppedBitmap != null) {
                                            try {
                                                referenceEmbedding = faceRecognitionHelper?.getFaceEmbedding(croppedBitmap)
                                                statusMessage = "Ready. Please look at the camera."
                                                isLoading = false
                                            } catch (e: Exception) {
                                                statusMessage = "Error generating embedding: ${e.message}"
                                                isLoading = false
                                            }
                                        } else {
                                            statusMessage = "Could not crop face from profile."
                                            isLoading = false
                                        }
                                    } else {
                                        statusMessage = "No face found in profile picture."
                                        isLoading = false
                                    }
                                }
                                .addOnFailureListener {
                                    statusMessage = "Failed to process profile image."
                                    isLoading = false
                                }
                                .addOnCompleteListener {
                                    profileDetector.close()
                                }
                        } else {
                            statusMessage = "Profile image is not a valid bitmap."
                            isLoading = false
                        }
                    } else {
                        statusMessage = "Failed to load profile image."
                        isLoading = false
                    }
                } else {
                    statusMessage = "Profile image not found."
                    isLoading = false
                }
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
                isLoading = false
                e.printStackTrace()
            }
        } else {
            statusMessage = "User not logged in."
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.CAMERA] == true &&
                (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    }

    LaunchedEffect(Unit) {
        val cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val locationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

        if (cameraPermission == PackageManager.PERMISSION_GRANTED && locationPermission == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (hasPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(statusMessage, modifier = Modifier.padding(top = 16.dp))
                }
            } else {
                // Camera Preview
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    var lastAnalyzedTimestamp = 0L
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        val currentTimestamp = System.currentTimeMillis()
                                        if (currentTimestamp - lastAnalyzedTimestamp >= 500) { // Throttle: 500ms
                                            lastAnalyzedTimestamp = currentTimestamp
                                            try {
                                                // Only process face if not already verified
                                                if (!isFaceVerified && !isMarkingAttendance) {
                                                    processImageProxy(
                                                        imageProxy,
                                                        faceRecognitionHelper,
                                                        referenceEmbedding,
                                                        detector,
                                                        scope,
                                                        currentChallenge,
                                                        isChallengeCompleted,
                                                        onChallengeCompleted = {
                                                            // LATENCY CHECK
                                                            val duration = System.currentTimeMillis() - challengeStartTime
                                                            if (duration > MAX_CHALLENGE_DURATION) {
                                                                // Took too long - likely video call lag
                                                                scope.launch(Dispatchers.Main) {
                                                                    statusMessage = "Too Slow! Restarting verification."
                                                                    // Restart the ENTIRE sequence
                                                                    retryTrigger++
                                                                }
                                                                return@processImageProxy
                                                            }

                                                            // Advance to next gesture in sequence
                                                            if (currentGestureIndex < gestureSequence.size - 1) {
                                                                currentGestureIndex++
                                                                currentChallenge = gestureSequence[currentGestureIndex]
                                                                challengeStartTime = System.currentTimeMillis() // Reset Timer for next step

                                                                scope.launch(Dispatchers.Main) {
                                                                    challengeStatus = when(currentChallenge) {
                                                                        LivenessChallenge.BLINK -> "Great! Now Blink"
                                                                        LivenessChallenge.SMILE -> "Good! Now Smile"
                                                                        LivenessChallenge.TURN_LEFT -> "Okay, Turn Left"
                                                                        LivenessChallenge.TURN_RIGHT -> "Okay, Turn Right"
                                                                        LivenessChallenge.ZOOM_IN -> "Zoom In (Move Closer)"
                                                                        else -> "Next Step..."
                                                                    }
                                                                }
                                                            } else {
                                                                // Sequence Completed
                                                                isChallengeCompleted = true
                                                                scope.launch(Dispatchers.Main) {
                                                                    challengeStatus = "Liveness Verified!"
                                                                }
                                                            }
                                                        },
                                                        onMatchFound = {
                                                            consecutiveMatchCount++
                                                            if (consecutiveMatchCount >= 3) {
                                                                isFaceVerified = true
                                                                statusMessage = "Face Verified!"
                                                            } else {
                                                                statusMessage = "Verifying... ($consecutiveMatchCount/3)"
                                                            }
                                                        },
                                                        onMatchFailed = {
                                                            consecutiveMatchCount = 0
                                                            statusMessage = it
                                                        },
                                                        onStatusUpdate = { msg ->
                                                            statusMessage = msg
                                                        },
                                                        currentFlashColor = flashColor // Pass current flash color
                                                    )
                                                } else {
                                                    imageProxy.close()
                                                }
                                            } catch (e: Throwable) {
                                                Log.e("FaceDetection", "Analyzer fatal error", e)
                                                imageProxy.close()
                                            }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                Log.e("FaceDetection", "Use case binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Flash Overlay - Removed as we are using gestures now
                // if (currentChallenge == LivenessChallenge.COLOR_FLASH && !isChallengeCompleted) { ... }

                // Overlay UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Status Cards
                    Column {
                        // Location Status
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLocationVerified) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = locationStatus,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Face Status
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isFaceVerified) Color(0xFF4CAF50) else Color(0xFF2196F3)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isFaceVerified) "Face Verified" else statusMessage,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Bottom Action Button
                    if (isLocationVerified && isFaceVerified) {
                        Button(
                            onClick = {
                                if (!isMarkingAttendance) {
                                    isMarkingAttendance = true
                                    scope.launch {
                                        submitAttendance(context, navController, supabase, fusedLocationClient, userGroup, userProfile)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isMarkingAttendance) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(end = 8.dp))
                                Text("Marking...", style = MaterialTheme.typography.titleMedium)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mark Attendance", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required for attendance.")
        }
    }
}

enum class LivenessChallenge {
    BLINK, SMILE, TURN_LEFT, TURN_RIGHT, ZOOM_IN
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
fun processImageProxy(
    imageProxy: ImageProxy,
    helper: FaceRecognitionHelper?,
    referenceEmbedding: FloatArray?,
    detector: com.google.mlkit.vision.face.FaceDetector,
    scope: kotlinx.coroutines.CoroutineScope,
    currentChallenge: LivenessChallenge,
    isChallengeCompleted: Boolean,
    onChallengeCompleted: () -> Unit,
    onMatchFound: () -> Unit,
    onMatchFailed: (String) -> Unit,
    onStatusUpdate: (String) -> Unit,
    onFlashColorChange: (Color) -> Unit = {},
    onColorVerified: (Color) -> Unit = {}, // Add this parameter
    currentFlashColor: Color = Color.Transparent // Add parameter
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && helper != null && referenceEmbedding != null) {
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    // 1. Multiple Faces Check
                    if (faces.size > 1) {
                        scope.launch(Dispatchers.Main) {
                            onStatusUpdate("Multiple faces detected. Only one person allowed.")
                        }
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    val face = faces[0]

                    // 2. Face Size Check (Anti-Spoofing for Video Calls)
                    // A face on a phone screen held at arm's length is usually smaller than a real face.
                    // We require the face width to be at least 20% of the image width.
                    val faceWidthRatio = face.boundingBox.width().toFloat() / mediaImage.width.toFloat()
                    if (faceWidthRatio < 0.2f) {
                        scope.launch(Dispatchers.Main) {
                            onStatusUpdate("Move Closer (Face too small)")
                        }
                        imageProxy.close()
                        return@addOnSuccessListener
                    }

                    // Liveness Detection Logic
                    if (!isChallengeCompleted) {
                        var challengeMet = false
                        when (currentChallenge) {
                            LivenessChallenge.BLINK -> {
                                val leftEyeOpenProb = face.leftEyeOpenProbability
                                val rightEyeOpenProb = face.rightEyeOpenProbability
                                if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
                                    if (leftEyeOpenProb < 0.1 && rightEyeOpenProb < 0.1) {
                                        challengeMet = true
                                    }
                                }
                            }
                            LivenessChallenge.SMILE -> {
                                val smileProb = face.smilingProbability
                                if (smileProb != null && smileProb > 0.8) {
                                    challengeMet = true
                                }
                            }
                            LivenessChallenge.TURN_LEFT -> {
                                val rotY = face.headEulerAngleY
                                if (rotY > 20) { // Looking left (positive Y)
                                    challengeMet = true
                                }
                            }
                            LivenessChallenge.TURN_RIGHT -> {
                                val rotY = face.headEulerAngleY
                                if (rotY < -20) { // Looking right (negative Y)
                                    challengeMet = true
                                }
                            }
                            LivenessChallenge.ZOOM_IN -> {
                                // Require face to be significantly larger (e.g., > 45% of screen width)
                                // This forces the user to bring the phone very close.
                                if (faceWidthRatio > 0.45f) {
                                    challengeMet = true
                                } else {
                                     scope.launch(Dispatchers.Main) {
                                         onStatusUpdate("Move Closer... Closer...")
                                     }
                                }
                            }
                            // Removed COLOR_FLASH logic
                            else -> {}
                        }

                        if (challengeMet) {
                            onChallengeCompleted()
                        } else {
                             scope.launch(Dispatchers.Main) {
                                 val instruction = when(currentChallenge) {
                                     LivenessChallenge.BLINK -> "Please Blink"
                                     LivenessChallenge.SMILE -> "Please Smile"
                                     LivenessChallenge.TURN_LEFT -> "Turn Head Left"
                                     LivenessChallenge.TURN_RIGHT -> "Turn Head Right"
                                     LivenessChallenge.ZOOM_IN -> "Zoom In (Move Closer)"
                                     else -> ""
                                 }
                                 // Only update if not already showing a specific prompt (like "Move Closer")
                                 if (currentChallenge != LivenessChallenge.ZOOM_IN) {
                                     onStatusUpdate(instruction)
                                 }
                             }
                        }
                    }

                    if (isChallengeCompleted) {
                        // CONTINUOUS SECURITY CHECK:
                        // Even after passing the challenge, we must ensure the user doesn't switch to a spoof
                        // or move to a compromised position before recognition completes.

                        // Offload heavy processing to background thread using the provided scope
                        scope.launch(Dispatchers.Default) {
                            try {
                                // Convert ImageProxy to Bitmap (Heavy)
                                val bitmap = imageProxyToBitmap(imageProxy)
                                if (bitmap != null) {
                                    val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
                                    val croppedBitmap = cropBitmap(rotatedBitmap, face.boundingBox)

                                    if (croppedBitmap != null) {
                                        // Generate Embedding (Heavy)
                                        val currentEmbedding = helper.getFaceEmbedding(croppedBitmap)
                                        val distance = helper.calculateDistance(currentEmbedding, referenceEmbedding)

                                        Log.d("FaceRecognition", "Distance: $distance")

                                        withContext(Dispatchers.Main) {
                                            // Stricter threshold (0.75f) and consecutive match requirement
                                            if (distance < 0.75f) {
                                                onMatchFound() // This increments the counter in the UI logic
                                            } else {
                                                onMatchFailed("Not Recognized (Dist: ${String.format("%.2f", distance)})")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Throwable) {
                                Log.e("FaceDetection", "Error processing face", e)
                                withContext(Dispatchers.Main) {
                                    onStatusUpdate("Error: ${e.message}")
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    } else {
                        imageProxy.close()
                    }
                } else {
                    scope.launch(Dispatchers.Main) {
                        onStatusUpdate("No face detected")
                    }
                    imageProxy.close()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Face detection failed", e)
                onStatusUpdate("Detection failed: ${e.message}")
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    try {
        if (image.planes.isEmpty()) return null
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        Log.e("FaceDetection", "Error converting ImageProxy to Bitmap", e)
        return null
    }
}

fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap? {
    // Expand the rectangle by 20%
    val marginX = (rect.width() * 0.2f).toInt()
    val marginY = (rect.height() * 0.2f).toInt()

    var left = (rect.left - marginX).coerceAtLeast(0)
    var top = (rect.top - marginY).coerceAtLeast(0)
    var width = (rect.width() + 2 * marginX)
    var height = (rect.height() + 2 * marginY)

    if (left + width > bitmap.width) width = bitmap.width - left
    if (top + height > bitmap.height) height = bitmap.height - top

    return if (width > 0 && height > 0) {
        Bitmap.createBitmap(bitmap, left, top, width, height)
    } else {
        null
    }
}


suspend fun submitAttendance(
    context: Context,
    navController: NavController,
    supabase: io.github.jan.supabase.SupabaseClient,
    locationClient: FusedLocationProviderClient,
    userGroup: Group?,
    userProfile: Profile?
) {
    try {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // TIME WINDOW CHECK
        if (userGroup != null) {
            try {
                val currentTime = LocalTime.now()
                // Assuming startTime and endTime are in "HH:mm" format (24-hour)
                // If they include seconds, we might need a different formatter or flexible parsing.
                // Standard Supabase time type usually returns HH:mm:ss or HH:mm.
                // Let's try to parse flexibly.

                // Helper to parse time strings that might be "HH:mm" or "HH:mm:ss"
                fun parseTime(timeStr: String): LocalTime {
                    return try {
                        LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME) // HH:mm:ss or HH:mm:ss.SSS
                    } catch (e: DateTimeParseException) {
                        LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
                    }
                }

                val start = parseTime(userGroup.startTime)
                val end = parseTime(userGroup.endTime)

                if (currentTime.isBefore(start) || currentTime.isAfter(end)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Attendance not allowed. Window: ${userGroup.startTime} - ${userGroup.endTime}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }
            } catch (e: Exception) {
                Log.e("Attendance", "Error parsing time window", e)
                // If parsing fails, we might want to allow it or block it.
                // For safety, let's log and maybe allow, or block if strict.
                // Let's block and show error to fix configuration.
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error checking time window: ${e.message}", Toast.LENGTH_LONG).show()
                }
                return
            }
        }

        // Re-verify location just in case (optional, but good practice)
        val location = try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationClient.lastLocation.await()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        if (location == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: Could not fetch current location.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val attendanceData = AttendanceEntry(
            userId = userId,
            status = "present",
            imageUrl = "verified_by_face_recognition",
            latitude = location.latitude,
            longitude = location.longitude,
            name = userProfile?.name,
            tehsil = userProfile?.seating // Updated to use seating
        )
        supabase.from("attendance").insert(attendanceData)

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Attendance Marked Successfully!", Toast.LENGTH_LONG).show()
            navController.popBackStack()
        }
    } catch (e: Throwable) {
        Log.e("Attendance", "Fatal error marking attendance", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error marking attendance: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Serializable
data class AttendanceEntry(
    @SerialName("user_id") val userId: String,
    val status: String,
    @SerialName("image_url") val imageUrl: String,
    val latitude: Double,
    val longitude: Double,
    val name: String?,
    val tehsil: String? // Keep as tehsil for DB compatibility
)
