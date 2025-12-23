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
import com.google.android.gms.location.LocationServices
import com.dbpsecurity.dbpcheckin.models.Group
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dbpsecurity.dbpcheckin.data.SupabaseClient
import com.dbpsecurity.dbpcheckin.models.Profile
import com.dbpsecurity.dbpcheckin.utils.FaceRecognitionHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.tasks.await

@Composable
fun FaceDetectionScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasPermission by remember { mutableStateOf(false) }
    var isFaceDetected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var referenceEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var faceRecognitionHelper by remember { mutableStateOf<FaceRecognitionHelper?>(null) }
    var userGroup by remember { mutableStateOf<Group?>(null) }
    var isMarkingAttendance by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val supabase = SupabaseClient.client
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            faceRecognitionHelper?.close()
        }
    }

    // Initialize FaceRecognitionHelper
    LaunchedEffect(Unit) {
        try {
            faceRecognitionHelper = FaceRecognitionHelper(context)
        } catch (e: Exception) {
            statusMessage = "Error: Model file not found. Please download mobile_face_net.tflite"
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

                if (profile?.groupId != null) {
                    userGroup = withContext(Dispatchers.IO) {
                        supabase.from("groups").select {
                            filter { eq("id", profile.groupId) }
                        }.decodeSingleOrNull<Group>()
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
                        val bitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                        statusMessage = "Generating reference embedding..."

                        // Detect face in profile image to crop it
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val detector = FaceDetection.getClient(
                            FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                .build()
                        )

                        detector.process(inputImage)
                            .addOnSuccessListener { faces ->
                                if (faces.isNotEmpty()) {
                                    val face = faces[0]
                                    val croppedBitmap = cropBitmap(bitmap, face.boundingBox)
                                    if (croppedBitmap != null) {
                                        referenceEmbedding = faceRecognitionHelper?.getFaceEmbedding(croppedBitmap)
                                        statusMessage = "Ready. Please look at the camera."
                                        isLoading = false
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
                                                if (!isMarkingAttendance) {
                                                    processImageProxy(
                                                        imageProxy,
                                                        faceRecognitionHelper,
                                                        referenceEmbedding,
                                                        onMatchFound = {
                                                            if (!isMarkingAttendance) {
                                                                isMarkingAttendance = true
                                                                scope.launch {
                                                                    try {
                                                                        markAttendance(context, navController, supabase, fusedLocationClient, userGroup)
                                                                    } catch (e: Exception) {
                                                                        Log.e("FaceDetection", "Crash prevented", e)
                                                                    } finally {
                                                                        isMarkingAttendance = false
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onStatusUpdate = { msg ->
                                                            // Optional: Update status message (careful with recomposition)
                                                        }
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

                Text(
                    text = statusMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required for attendance.")
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
fun processImageProxy(
    imageProxy: ImageProxy,
    helper: FaceRecognitionHelper?,
    referenceEmbedding: FloatArray?,
    onMatchFound: () -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && helper != null && referenceEmbedding != null) {
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                try {
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        // Convert ImageProxy to Bitmap
                        val bitmap = imageProxyToBitmap(imageProxy)
                        val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
                        val croppedBitmap = cropBitmap(rotatedBitmap, face.boundingBox)

                        if (croppedBitmap != null) {
                            val currentEmbedding = helper.getFaceEmbedding(croppedBitmap)
                            val distance = helper.calculateDistance(currentEmbedding, referenceEmbedding)

                            Log.d("FaceRecognition", "Distance: $distance")

                            // Threshold for MobileFaceNet is typically around 0.8 - 1.0 depending on normalization
                            if (distance < 0.8f) {
                                onMatchFound()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("FaceDetection", "Error processing face", e)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Face detection failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    try {
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
        // Return a 1x1 empty bitmap to prevent null checks from crashing downstream,
        // though downstream should handle it.
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}

fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap? {
    var left = rect.left.coerceAtLeast(0)
    var top = rect.top.coerceAtLeast(0)
    var width = rect.width()
    var height = rect.height()

    if (left + width > bitmap.width) width = bitmap.width - left
    if (top + height > bitmap.height) height = bitmap.height - top

    return if (width > 0 && height > 0) {
        Bitmap.createBitmap(bitmap, left, top, width, height)
    } else {
        null
    }
}

suspend fun markAttendance(
    context: Context,
    navController: NavController,
    supabase: io.github.jan.supabase.SupabaseClient,
    locationClient: FusedLocationProviderClient,
    userGroup: Group?
) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Face Verified! Checking Location...", Toast.LENGTH_SHORT).show()
    }

    try {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Check Location
        if (userGroup == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: User not assigned to a group.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val location = try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationClient.lastLocation.await()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("Attendance", "Location error", e)
            null
        }

        if (location == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: Could not fetch current location.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            userGroup.latitude, userGroup.longitude,
            results
        )
        val distanceInMeters = results[0]

        // Allow dynamic radius from group settings (default 100m)
        val allowedRadius = userGroup.radius
        if (distanceInMeters > allowedRadius) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "You are too far from the office! Distance: ${distanceInMeters.toInt()}m (Allowed: ${allowedRadius.toInt()}m)", Toast.LENGTH_LONG).show()
            }
            return
        }

        val attendanceData = mapOf(
            "user_id" to userId,
            "status" to "present",
            "image_url" to "verified_by_face_recognition",
            "latitude" to location.latitude,
            "longitude" to location.longitude
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
