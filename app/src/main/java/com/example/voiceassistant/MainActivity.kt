// MainActivity.kt
package com.example.voiceassistant

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*

class MainActivity : ComponentActivity() {

    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            if (it.value) {
                Toast.makeText(this, "${it.key} granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request all necessary permissions
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        multiplePermissionLauncher.launch(permissions)

        setContent {
            VoiceAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceAssistantScreen()
                }
            }
        }
    }
}

@Composable
fun VoiceAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1976D2),
            secondary = Color(0xFF42A5F5),
            background = Color(0xFFF5F5F5),
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF333333),
            onSurface = Color(0xFF333333)
        ),
        content = content
    )
}

data class Feature(
    val name: String,
    val icon: ImageVector,
    val description: String,
    val example: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isListening by remember { mutableStateOf(false) }
    var userCommand by remember { mutableStateOf("Press the button to start") }
    var assistantResponse by remember { mutableStateOf("Waiting for your command...") }
    var isProcessing by remember { mutableStateOf(false) }
    var showFeatures by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }

    val features = remember {
        listOf(
            Feature("Web Search", Icons.Default.Search, "Search anything on the web", "Search for pizza recipe"),
            Feature("Weather", Icons.Default.Cloud, "Get current weather information", "What's the weather in Mumbai?"),
            Feature("Open Apps", Icons.Default.Apps, "Launch any installed app", "Open YouTube"),
            Feature("Set Alarm", Icons.Default.Alarm, "Set alarms and timers", "Set alarm for 7 AM"),
            Feature("Make Calls", Icons.Default.Call, "Call your contacts", "Call John"),
            Feature("Send SMS", Icons.AutoMirrored.Filled.Message, "Send text messages", "Send message to Mom"),
            Feature("Flashlight", Icons.Default.FlashlightOn, "Control flashlight", "Turn on flashlight"),
            Feature("Volume Control", Icons.AutoMirrored.Filled.VolumeUp, "Adjust volume", "Increase volume"),
            Feature("Camera", Icons.Default.CameraAlt, "Take photos or videos", "Open camera"),
            Feature("Bluetooth", Icons.Default.Bluetooth, "Toggle Bluetooth", "Turn on Bluetooth"),
            Feature("WiFi", Icons.Default.Wifi, "Toggle WiFi", "Turn off WiFi"),
            Feature("Brightness", Icons.Default.Brightness6, "Adjust screen brightness", "Increase brightness"),
            Feature("AI Chat", Icons.Default.Psychology, "Ask anything with Gemini AI", "Tell me about space"),
            Feature("Navigation", Icons.Default.Navigation, "Get directions", "Navigate to airport"),
            Feature("Play Music", Icons.Default.MusicNote, "Play songs", "Play some music"),
            Feature("Battery Info", Icons.Default.BatteryFull, "Check battery status", "Battery level"),
            Feature("Calculator", Icons.Default.Calculate, "Do calculations", "Calculate 25 times 4"),
            Feature("Screenshot", Icons.Default.Screenshot, "Take screenshots", "Take a screenshot")
        )
    }

    DisposableEffect(Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
            }
        }

        onDispose {
            speechRecognizer?.destroy()
            textToSpeech?.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                userCommand = "Listening..."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                isProcessing = false
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    else -> "Error occurred"
                }
                userCommand = errorMessage
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    userCommand = "You said: $command"
                    isProcessing = true

                    scope.launch {
                        val response = processAdvancedCommand(command, context) { newFlashState ->
                            flashOn = newFlashState
                        }
                        assistantResponse = response
                        isProcessing = false
                        textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Voice Assistant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    if (flashOn) {
                        Icon(
                            imageVector = Icons.Default.FlashlightOn,
                            contentDescription = "Flash On",
                            tint = Color.Yellow,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { showFeatures = !showFeatures }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Features",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showFeatures) {
                FeaturesScreen(
                    features = features,
                    onDismiss = { showFeatures = false }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    CommandCard(
                        title = "Your Command",
                        content = userCommand,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    VoiceButton(
                        isListening = isListening,
                        isProcessing = isProcessing,
                        onClick = {
                            if (isListening) {
                                speechRecognizer?.stopListening()
                                isListening = false
                            } else {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    }
                                    speechRecognizer?.startListening(intent)
                                    isListening = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Microphone permission required",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    CommandCard(
                        title = "Assistant Response",
                        content = assistantResponse,
                        contentColor = MaterialTheme.colorScheme.primary,
                        isProcessing = isProcessing
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun FeaturesScreen(features: List<Feature>, onDismiss: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Available Features",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        items(features) { feature ->
            FeatureCard(feature = feature)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
fun FeatureCard(feature: Feature) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feature.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = feature.description,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Example: \"${feature.example}\"",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun CommandCard(
    title: String,
    content: String,
    contentColor: Color,
    isProcessing: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF666666)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isProcessing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Processing...",
                        fontSize = 16.sp,
                        color = contentColor
                    )
                }
            } else {
                Text(
                    text = content,
                    fontSize = 16.sp,
                    color = contentColor,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
fun VoiceButton(
    isListening: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(160.dp),
            containerColor = if (isProcessing) Color.Gray else MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone",
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        isProcessing -> "Processing..."
                        isListening -> "Listening..."
                        else -> "Tap to Speak"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

suspend fun processAdvancedCommand(command: String, context: Context, onFlashChange: (Boolean) -> Unit): String {
    val lowerCommand = command.lowercase(Locale.getDefault())

    return when {
        // Flashlight Control
        lowerCommand.contains("flash") || lowerCommand.contains("torch") -> {
            val turnOn = lowerCommand.contains("on") || lowerCommand.contains("enable")
            toggleFlashlight(context, turnOn, onFlashChange)
        }

        // Make Phone Call
        lowerCommand.contains("call") -> {
            val contact = extractContactName(lowerCommand)
            makePhoneCall(context, contact)
        }

        // Send SMS
        lowerCommand.contains("send message") || lowerCommand.contains("text") -> {
            val contact = extractContactName(lowerCommand)
            sendSMS(context, contact)
        }

        // Volume Control
        lowerCommand.contains("volume") -> {
            adjustVolume(context, lowerCommand)
        }

        // Camera
        lowerCommand.contains("camera") || lowerCommand.contains("photo") || lowerCommand.contains("picture") -> {
            openCamera(context, lowerCommand.contains("video"))
        }

        // Bluetooth
        lowerCommand.contains("bluetooth") -> {
            openBluetoothSettings(context)
        }

        // WiFi
        lowerCommand.contains("wifi") || lowerCommand.contains("wi-fi") -> {
            openWifiSettings(context)
        }

        // Brightness
        lowerCommand.contains("brightness") -> {
            openBrightnessSettings(context)
        }

        // Battery
        lowerCommand.contains("battery") -> {
            getBatteryInfo(context)
        }

        // Navigation
        lowerCommand.contains("navigate") || lowerCommand.contains("direction") -> {
            val destination = extractDestination(lowerCommand)
            openNavigation(context, destination)
        }

        // Play Music
        lowerCommand.contains("play music") || lowerCommand.contains("play song") -> {
            playMusic(context)
        }

        // Calculator
        lowerCommand.contains("calculate") || lowerCommand.contains("multiply") ||
                lowerCommand.contains("divide") || lowerCommand.contains("plus") || lowerCommand.contains("minus") -> {
            calculate(lowerCommand)
        }

        // Screenshot
        lowerCommand.contains("screenshot") || lowerCommand.contains("screen capture") -> {
            "To take a screenshot, press Power + Volume Down buttons simultaneously"
        }

        // Web Search
        lowerCommand.contains("search") || lowerCommand.contains("google") -> {
            val query = lowerCommand
                .replace("search for", "")
                .replace("search", "")
                .replace("google", "")
                .trim()

            if (query.isNotEmpty()) {
                searchWeb(query, context)
                "Searching the web for: $query"
            } else {
                "What would you like me to search for?"
            }
        }

        // Weather
        lowerCommand.contains("weather") -> {
            val city = extractCity(lowerCommand)
            getWeather(city)
        }

        // Open Apps
        lowerCommand.contains("open") -> {
            openApp(context, lowerCommand)
        }

        // Set Alarm
        lowerCommand.contains("alarm") || lowerCommand.contains("wake me") -> {
            val time = extractTime(lowerCommand)
            setAlarm(context, time)
        }

        // Gemini AI Integration
        lowerCommand.contains("tell me about") ||
                lowerCommand.contains("what is") ||
                lowerCommand.contains("explain") ||
                lowerCommand.contains("who is") ||
                lowerCommand.contains("how to") -> {
            askGemini(command)
        }

        // Time and Date
        lowerCommand.contains("time") -> {
            val currentTime = Calendar.getInstance()
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)
            val amPm = if (hour >= 12) "PM" else "AM"
            val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
            "The current time is $displayHour:${String.format("%02d", minute)} $amPm"
        }
        lowerCommand.contains("date") -> {
            val currentDate = Calendar.getInstance()
            val day = currentDate.get(Calendar.DAY_OF_MONTH)
            val month = currentDate.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
            val year = currentDate.get(Calendar.YEAR)
            "Today is $month $day, $year"
        }

        // Basic conversation
        lowerCommand.contains("hello") || lowerCommand.contains("hi") -> {
            "Hello! I'm your AI assistant powered by Gemini. I have full control of your phone. Try asking me to turn on flashlight, make a call, check weather, or anything else!"
        }
        lowerCommand.contains("how are you") -> {
            "I'm functioning perfectly! Ready to help you with complete phone control."
        }
        lowerCommand.contains("thank") -> {
            "You're very welcome! I'm always here to help."
        }

        else -> {
            "I heard: $command. Try commands like: turn on flashlight, call John, check weather, open camera, or ask me anything!"
        }
    }
}

// Flashlight Control
fun toggleFlashlight(context: Context, turnOn: Boolean, onFlashChange: (Boolean) -> Unit): String {
    return try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]
        cameraManager.setTorchMode(cameraId, turnOn)
        onFlashChange(turnOn)
        if (turnOn) "Flashlight turned on" else "Flashlight turned off"
    } catch (e: Exception) {
        "Unable to control flashlight: ${e.message}"
    }
}

// Make Phone Call
//fun makePhoneCall(context: Context, contact: String): String {
//    return try {
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
//            == PackageManager.PERMISSION_GRANTED) {
//
//            // For demo, using a placeholder number
//            val intent = Intent(Intent.ACTION_CALL).apply {
//                data = Uri.parse("tel:1234567890") // Replace with actual contact lookup
//            }
//            context.startActivity(intent)
//            "Calling $contact..."
//        } else {
//            "Phone permission required to make calls"
//        }
//    } catch (e: Exception) {
//        "Unable to make call: ${e.message}"
//    }
//}

fun makePhoneCall(context: Context, contactOrNumber: String): String {
    return try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val phoneNumber = resolveContactToPhoneNumber(context, contactOrNumber)
            if (phoneNumber.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                context.startActivity(intent)
                "Calling $contactOrNumber..."
            } else {
                "Could not find the number for $contactOrNumber."
            }
        } else {
            "Phone call permission required."
        }
    } catch (e: Exception) {
        "Unable to make call: ${e.message}"
    }
}

fun resolveContactToPhoneNumber(context: Context, contactName: String): String {
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
    val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf("%$contactName%")
    val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
    var phoneNumber = ""
    cursor?.use {
        if (it.moveToFirst()) {
            phoneNumber = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
        }
    }
    return phoneNumber
}

// Send SMS
fun sendSMS(context: Context, contact: String): String {
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:")
            putExtra("sms_body", "Hello!")
        }
        context.startActivity(intent)
        "Opening SMS for $contact"
    } catch (e: Exception) {
        "Unable to send SMS: ${e.message}"
    }
}

// Volume Control
fun adjustVolume(context: Context, command: String): String {
    return try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        when {
            command.contains("increase") || command.contains("up") || command.contains("raise") -> {
                val newVolume = minOf(currentVolume + 2, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                "Volume increased to ${(newVolume * 100) / maxVolume}%"
            }
            command.contains("decrease") || command.contains("down") || command.contains("lower") -> {
                val newVolume = maxOf(currentVolume - 2, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                "Volume decreased to ${(newVolume * 100) / maxVolume}%"
            }
            command.contains("mute") -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                "Volume muted"
            }
            command.contains("max") || command.contains("full") -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                "Volume set to maximum"
            }
            else -> {
                "Current volume is ${(currentVolume * 100) / maxVolume}%"
            }
        }
    } catch (e: Exception) {
        "Unable to adjust volume: ${e.message}"
    }
}

// Camera
fun openCamera(context: Context, isVideo: Boolean): String {
    return try {
        val intent = if (isVideo) {
            Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        } else {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        }
        context.startActivity(intent)
        if (isVideo) "Opening video camera" else "Opening camera"
    } catch (e: Exception) {
        "Unable to open camera: ${e.message}"
    }
}

// Bluetooth Settings
fun openBluetoothSettings(context: Context): String {
    return try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        "Opening Bluetooth settings"
    } catch (e: Exception) {
        "Unable to open Bluetooth settings"
    }
}

// WiFi Settings
fun openWifiSettings(context: Context): String {
    return try {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        "Opening WiFi settings"
    } catch (e: Exception) {
        "Unable to open WiFi settings"
    }
}

// Brightness Settings
fun openBrightnessSettings(context: Context): String {
    return try {
        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        "Opening display settings"
    } catch (e: Exception) {
        "Unable to open display settings"
    }
}

// Battery Info
fun getBatteryInfo(context: Context): String {
    return try {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = (level * 100) / scale

        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING

        if (isCharging) {
            "Battery is at $percentage% and charging"
        } else {
            "Battery is at $percentage%"
        }
    } catch (e: Exception) {
        "Unable to get battery info"
    }
}

// Navigation
fun openNavigation(context: Context, destination: String): String {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$destination"))
        intent.setPackage("com.google.android.apps.maps")
        context.startActivity(intent)
        "Navigating to $destination"
    } catch (e: Exception) {
        "Unable to open navigation. Make sure Google Maps is installed."
    }
}

// Play Music
fun playMusic(context: Context): String {
    return try {
        val intent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)
        context.startActivity(intent)
        "Opening music player"
    } catch (e: Exception) {
        "Unable to open music player"
    }
}

// Calculator
fun calculate(command: String): String {
    return try {
        val numbers = Regex("\\d+").findAll(command).map { it.value.toInt() }.toList()

        if (numbers.size < 2) {
            return "Please provide two numbers for calculation"
        }

        val result = when {
            command.contains("plus") || command.contains("+") || command.contains("add") -> {
                numbers[0] + numbers[1]
            }
            command.contains("minus") || command.contains("-") || command.contains("subtract") -> {
                numbers[0] - numbers[1]
            }
            command.contains("multiply") || command.contains("times") || command.contains("×") -> {
                numbers[0] * numbers[1]
            }
            command.contains("divide") || command.contains("÷") -> {
                if (numbers[1] != 0) numbers[0].toDouble() / numbers[1] else return "Cannot divide by zero"
            }
            else -> return "Unknown operation"
        }

        "${numbers[0]} and ${numbers[1]} equals $result"
    } catch (e: Exception) {
        "Unable to calculate. Try: Calculate 25 times 4"
    }
}

// Gemini AI Integration
suspend fun askGemini(question: String): String = withContext(Dispatchers.IO) {
    try {
        val apiKey = "AIzaSyCdX06FXPPXLThPMqGhrdABNt6IQD0vimI" // Replace with your Gemini API key
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$apiKey"

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", question)
                        })
                    })
                })
            })
        }

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        connection.outputStream.use { os ->
            os.write(requestBody.toString().toByteArray())
        }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            val text = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            text.take(300) + if (text.length > 300) "..." else "" // Limit response length
        } else {
            "Gemini API error. Please check your API key and internet connection."
        }
    } catch (e: Exception) {
        "Unable to connect to Gemini AI. Error: ${e.message}. Please add your API key in the code."
    }
}

fun searchWeb(query: String, context: Context) {
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encodedQuery"))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to search", Toast.LENGTH_SHORT).show()
    }
}

suspend fun getWeather(city: String): String = withContext(Dispatchers.IO) {
    try {
        val apiKey = "dc7e8ac8862261915194f86099449d69" // Get free key from openweathermap.org
        val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$apiKey&units=metric"

        val response = URL(url).readText()
        val json = JSONObject(response)

        val temp = json.getJSONObject("main").getDouble("temp").toInt()
        val description = json.getJSONArray("weather").getJSONObject(0).getString("description")
        val humidity = json.getJSONObject("main").getInt("humidity")
        val feelsLike = json.getJSONObject("main").getDouble("feels_like").toInt()

        "Weather in $city: $temp°C, feels like $feelsLike°C, $description, Humidity: $humidity%"
    } catch (e: Exception) {
        "Weather information unavailable. Please add OpenWeather API key or check internet connection."
    }
}

fun extractCity(command: String): String {
    val words = command.split(" ")
    val cityIndex = words.indexOfFirst { it in listOf("in", "at", "for") }
    return if (cityIndex != -1 && cityIndex < words.size - 1) {
        words[cityIndex + 1].capitalize()
    } else {
        "Hyderabad"
    }
}

fun extractContactName(command: String): String {
    val words = command.split(" ")
    val callIndex = words.indexOfFirst { it.contains("call") || it.contains("message") || it.contains("text") }
    return if (callIndex != -1 && callIndex < words.size - 1) {
        words.drop(callIndex + 1).joinToString(" ").trim()
    } else {
        "contact"
    }
}

fun extractDestination(command: String): String {
    val words = command.split(" ")
    val toIndex = words.indexOfFirst { it == "to" }
    return if (toIndex != -1 && toIndex < words.size - 1) {
        words.drop(toIndex + 1).joinToString(" ").trim()
    } else {
        "destination"
    }
}

fun openApp(context: Context, command: String): String {
    return when {
        command.contains("youtube") -> {
            openAppByPackage(context, "com.google.android.youtube", "YouTube")
        }
        command.contains("gmail") || command.contains("email") -> {
            openAppByPackage(context, "com.google.android.gm", "Gmail")
        }
        command.contains("chrome") || command.contains("browser") -> {
            openAppByPackage(context, "com.android.chrome", "Chrome")
        }
        command.contains("maps") -> {
            openAppByPackage(context, "com.google.android.apps.maps", "Google Maps")
        }
        command.contains("whatsapp") -> {
            openAppByPackage(context, "com.whatsapp", "WhatsApp")
        }
        command.contains("instagram") -> {
            openAppByPackage(context, "com.instagram.android", "Instagram")
        }
        command.contains("spotify") -> {
            openAppByPackage(context, "com.spotify.music", "Spotify")
        }
        command.contains("twitter") || command.contains("x") -> {
            openAppByPackage(context, "com.twitter.android", "Twitter")
        }
        command.contains("facebook") -> {
            openAppByPackage(context, "com.facebook.katana", "Facebook")
        }
        command.contains("settings") -> {
            openSettings(context)
            "Opening settings"
        }
        command.contains("gallery") || command.contains("photos") -> {
            openAppByPackage(context, "com.google.android.apps.photos", "Gallery")
        }
        command.contains("calculator") -> {
            openAppByPackage(context, "com.google.android.calculator", "Calculator")
        }
        else -> "Which app would you like to open?"
    }
}

fun openAppByPackage(context: Context, packageName: String, appName: String): String {
    return try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
            "Opening $appName"
        } else {
            "$appName is not installed on your device"
        }
    } catch (e: Exception) {
        "Unable to open $appName"
    }
}

fun openSettings(context: Context) {
    try {
        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open settings", Toast.LENGTH_SHORT).show()
    }
}

fun extractTime(command: String): Pair<Int, Int> {
    val timePattern = "(\\d{1,2})\\s*(:|\\s)\\s*(\\d{2})?\\s*(am|pm)?".toRegex(RegexOption.IGNORE_CASE)
    val match = timePattern.find(command)

    return if (match != null) {
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[3].toIntOrNull() ?: 0
        val amPm = match.groupValues[4].lowercase()

        if (amPm == "pm" && hour < 12) hour += 12
        if (amPm == "am" && hour == 12) hour = 0

        Pair(hour, minute)
    } else {
        Pair(7, 0)
    }
}

fun setAlarm(context: Context, time: Pair<Int, Int>): String {
    return try {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, time.first)
            putExtra(AlarmClock.EXTRA_MINUTES, time.second)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        context.startActivity(intent)
        val amPm = if (time.first >= 12) "PM" else "AM"
        val displayHour = if (time.first > 12) time.first - 12 else if (time.first == 0) 12 else time.first
        "Setting alarm for $displayHour:${String.format("%02d", time.second)} $amPm"
    } catch (e: Exception) {
        "Unable to set alarm. Please check your permissions."
    }
}
