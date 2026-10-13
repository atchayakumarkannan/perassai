package com.ak.assistant

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Locale

private val Bg = Color(0xFF101014)
private val Panel = Color(0xFF1B1B22)
private val Accent = Color(0xFFB2A5FF)
private val Muted = Color(0xFFAAA9B7)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 300
            )
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                TaurusScreen(onSpeak = { speak(it) })
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text.take(1500), TextToSpeech.QUEUE_FLUSH, null, "taurus-reply")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

private data class ChatMessage(val text: String, val fromUser: Boolean)
private data class DeviceApp(val label: String, val packageName: String)

@Composable
private fun TaurusScreen(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val memory = remember { LocalMemory(context) }
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(memory.apiKey()) }
    var showSettings by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(memory.speakReplies()) }
    var busy by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("Chat") }
    var apps by remember { mutableStateOf(memory.selectedApps()) }
    var defaultMusic by remember { mutableStateOf(memory.defaultMusicPackage()) }
    var reminders by remember { mutableStateOf(memory.reminders()) }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf("30") }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageStatus by remember { mutableStateOf("") }
    var filter by remember { mutableFloatStateOf(0f) }
    var messages by remember {
        mutableStateOf(listOf(ChatMessage("Hi, I'm TAURUS. I can chat with Gemini, save on-device memories, open apps, search music, set alarms, schedule reminders, and apply basic photo filters. Add your Gemini API key in Settings for AI chat.", false)))
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input = it }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        imageStatus = if (uri != null) "Image loaded. Choose a look and save a copy." else ""
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bitmap = try { decodeScaledBitmap(context, uri) } catch (_: Exception) { null }
                withContext(Dispatchers.Main) {
                    imageBitmap = bitmap
                    filter = 0f
                    if (bitmap == null) imageStatus = "Couldn't load this image."
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("TAURUS settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gemini API key", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("Paste your API key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Cloud AI sends prompts and relevant memory to Google's API. Free quotas and model availability can change.",
                        color = Muted, fontSize = 12.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Speak replies", modifier = Modifier.weight(1f))
                        Switch(checked = speakReplies, onCheckedChange = {
                            speakReplies = it
                            memory.setSpeakReplies(it)
                        })
                    }
                    TextButton(onClick = {
                        memory.clearMemories()
                        messages = messages + ChatMessage("Saved memories cleared.", false)
                    }) { Text("Clear saved memories") }
                }
            },
            confirmButton = {
                TextButton(onClick = { memory.setApiKey(apiKey.trim()); showSettings = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } },
            containerColor = Panel
        )
    }

    if (showAppPicker) {
        val installed = remember { getInstalledApps(context) }
        var search by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Add an app") },
            text = {
                Column(Modifier.heightIn(max = 520.dp)) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it }, singleLine = true,
                        placeholder = { Text("Search installed apps") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(installed.filter { it.label.contains(search, true) }.take(150), key = { it.packageName }) { app ->
                            val already = apps.any { it.packageName == app.packageName }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    apps = if (already) apps.filterNot { it.packageName == app.packageName }
                                    else (apps + SavedApp(app.label, app.packageName)).distinctBy { it.packageName }
                                    memory.saveSelectedApps(apps)
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, color = Color.White)
                                    Text(app.packageName, color = Muted, fontSize = 10.sp)
                                }
                                Icon(
                                    if (already) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = if (already) "Added" else "Add",
                                    tint = if (already) Accent else Muted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppPicker = false }) { Text("Done") } },
            containerColor = Panel
        )
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = Bg)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("TAURUS", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Your personal AI assistant", fontSize = 12.sp, color = Muted)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Chat", "Apps", "Reminders", "Images", "Memory").forEach { tab ->
                Surface(
                    onClick = { screen = tab },
                    color = if (screen == tab) Accent else Panel,
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)
                ) {
                    Text(tab, color = if (screen == tab) Bg else Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }

        when (screen) {
            "Chat" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    QuickAction("Remember") { input = "Remember that " }
                    QuickAction("My memory") { input = "What do you remember?" }
                    QuickAction("Play music") { input = "Play music " }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                            Column(Modifier.widthIn(max = 310.dp).background(if (msg.fromUser) Color(0xFF37304F) else Panel, RoundedCornerShape(18.dp)).padding(13.dp)) {
                                Text(msg.text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
                                if (!msg.fromUser) {
                                    TextButton(onClick = { onSpeak(msg.text) }) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp)); Text("Speak", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (busy) item { Text("TAURUS is thinking…", color = Muted, modifier = Modifier.padding(8.dp)) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).background(Panel, RoundedCornerShape(28.dp)).padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to TAURUS")
                        }
                        try { voiceLauncher.launch(intent) }
                        catch (_: ActivityNotFoundException) { messages = messages + ChatMessage("Speech recognition isn't available on this phone.", false) }
                    }) { Icon(Icons.Default.Mic, contentDescription = "Speak", tint = Accent) }
                    BasicTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        decorationBox = { inner -> if (input.isEmpty()) Text("Ask TAURUS anything…", color = Muted); inner() }
                    )
                    TextButton(enabled = input.isNotBlank() && !busy, onClick = {
                        val question = input.trim()
                        input = ""
                        messages = messages + ChatMessage(question, true)
                        busy = true
                        scope.launch {
                            val actionResult = runLocalCommand(context, question, memory, apps) { title, at ->
                                val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                                memory.addReminder(id, title, at)
                                scheduleReminder(context, id, title, at)
                                reminders = memory.reminders()
                            }
                            val response = actionResult ?: if (memory.apiKey().isBlank()) {
                                "Add a Gemini API key in Settings for general AI chat. I can still handle supported phone commands, reminders, and local memories."
                            } else {
                                GeminiClient.reply(memory.apiKey(), question, memory.allMemories())
                            }
                            messages = messages + ChatMessage(response, false)
                            if (speakReplies) onSpeak(response)
                            busy = false
                        }
                    }) { Text("Send") }
                }
            }

            "Apps" -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your apps", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = { showAppPicker = true }) { Icon(Icons.Default.Add, null); Text("Add app") }
                }
                Text("Choose apps TAURUS can open. Select a default music app for music commands.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(if (defaultMusic == app.packageName) "Default music app" else app.packageName, color = Muted, fontSize = 10.sp)
                            }
                            TextButton(onClick = { launchApp(context, app.packageName) }) { Text("Open") }
                            TextButton(onClick = { defaultMusic = app.packageName; memory.setDefaultMusicPackage(app.packageName) }) {
                                Text(if (defaultMusic == app.packageName) "Default ✓" else "Music", fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                apps = apps.filterNot { it.packageName == app.packageName }
                                if (defaultMusic == app.packageName) { defaultMusic = ""; memory.setDefaultMusicPackage("") }
                                memory.saveSelectedApps(apps)
                            }) { Text("×") }
                        }
                    }
                    if (apps.isEmpty()) item { Text("No apps added yet. Tap Add app to choose installed apps.", color = Muted, modifier = Modifier.padding(16.dp)) }
                }
                Text("Try: ‘Open Spotify’, ‘Open YouTube Music’, or ‘Play music Coldplay’.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            }

            "Reminders" -> {
                Text("Reminders", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("Create a reminder that will notify you later.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(value = reminderTitle, onValueChange = { reminderTitle = it }, label = { Text("What should I remind you about?") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reminderMinutes, onValueChange = { reminderMinutes = it.filter(Char::isDigit).take(5) }, label = { Text("Remind me in how many minutes?") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val mins = reminderMinutes.toLongOrNull()
                    if (reminderTitle.isNotBlank() && mins != null && mins > 0) {
                        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                        val at = System.currentTimeMillis() + mins * 60_000L
                        memory.addReminder(id, reminderTitle.trim(), at)
                        scheduleReminder(context, id, reminderTitle.trim(), at)
                        reminders = memory.reminders()
                        reminderTitle = ""
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Schedule reminder")
                }
                Text("Scheduled", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reminders, key = { it.id }) { r ->
                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, color = Color.White)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(r.at), color = Muted, fontSize = 11.sp)
                            }
                            TextButton(onClick = { cancelReminder(context, r.id); memory.removeReminder(r.id); reminders = memory.reminders() }) { Text("Cancel") }
                        }
                    }
                    if (reminders.isEmpty()) item { Text("No scheduled reminders.", color = Muted) }
                }
            }

            "Images" -> {
                Text("Image studio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("Choose a photo, apply an offline filter, and save an edited copy. Generative AI editing is not included in this screen yet.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Image, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Choose photo")
                }
                if (imageBitmap != null) {
                    AndroidView(
                        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                        update = { view -> view.setImageBitmap(applyImageFilter(imageBitmap!!, filter)) },
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text("Look", color = Color.White)
                    Slider(value = filter, onValueChange = { filter = it }, valueRange = 0f..3f, steps = 2)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Original", color = Muted, fontSize = 10.sp); Text("B&W", color = Muted, fontSize = 10.sp)
                        Text("Warm", color = Muted, fontSize = 10.sp); Text("Cool", color = Muted, fontSize = 10.sp)
                    }
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val output = try { saveEditedImage(context, applyImageFilter(imageBitmap!!, filter)) } catch (_: Exception) { null }
                            withContext(Dispatchers.Main) { imageStatus = if (output != null) "Edited copy saved to Pictures/TAURUS." else "Couldn't save the edited image." }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save edited copy") }
                } else {
                    Spacer(Modifier.weight(1f))
                    Text("Your selected photo will appear here.", color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                }
                if (imageStatus.isNotBlank()) Text(imageStatus, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            }

            else -> {
                Text("On-device memory", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("Only memories you explicitly save are stored here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                val saved = memory.allMemories().lines().filter { it.isNotBlank() }
                LazyColumn(Modifier.weight(1f)) {
                    items(saved) { line -> Text(line, color = Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)) }
                    if (saved.isEmpty()) item { Text("No memories saved yet. In Chat, say ‘Remember that …’", color = Muted) }
                }
                OutlinedButton(onClick = { memory.clearMemories() }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Clear all memories") }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, action: () -> Unit) {
    Surface(onClick = action, color = Panel, shape = RoundedCornerShape(18.dp)) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

private fun getInstalledApps(context: Context): List<DeviceApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val resolves = context.packageManager.queryIntentActivities(intent, 0)
    return resolves.mapNotNull { result ->
        val info = result.activityInfo ?: return@mapNotNull null
        DeviceApp(result.loadLabel(context.packageManager).toString(), info.packageName)
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private suspend fun runLocalCommand(
    context: Context,
    text: String,
    memory: LocalMemory,
    selectedApps: List<SavedApp>,
    createReminder: (String, Long) -> Unit
): String? {
    val q = text.trim()
    val lower = q.lowercase(Locale.getDefault())
    return when {
        lower.startsWith("remember that ") || lower.startsWith("remember ") || lower.startsWith("save this:") -> {
            val value = q.substringAfter("remember that ", q.substringAfter("remember ", q.substringAfter("save this:", ""))).trim()
            if (value.isBlank()) "Tell me what you'd like me to remember."
            else { memory.addMemory(value); "Saved to your on-device memory: $value" }
        }
        lower.contains("what do you remember") || lower == "my memories" || lower.contains("show my memories") -> {
            val all = memory.allMemories()
            if (all.isBlank()) "You haven't saved any memories yet. Say ‘Remember that …’ to save one." else "Here are your saved memories:\n$all"
        }
        lower.startsWith("forget all memories") || lower.startsWith("clear my memories") -> {
            memory.clearMemories(); "Done. I've cleared your saved memories."
        }
        lower.startsWith("remind me to ") || lower.startsWith("remind me ") -> {
            val parsed = parseRelativeReminder(q)
            if (parsed == null) "Try a reminder like ‘Remind me to study in 20 minutes’ or ‘Remind me to call home in 2 hours’."
            else { createReminder(parsed.first, parsed.second); "Reminder scheduled: ${parsed.first} — ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(parsed.second)}." }
        }
        lower.startsWith("open app ") -> {
            val name = q.substringAfter("open app ").trim()
            val app = selectedApps.firstOrNull { it.label.equals(name, true) } ?: selectedApps.firstOrNull { it.label.contains(name, true) }
            if (app == null) "I couldn't find '$name' in your app list. Add it from the Apps tab first."
            else if (launchApp(context, app.packageName)) "Opening ${app.label}." else "I couldn't open ${app.label}."
        }
        lower.contains("open spotify") -> openKnownApp(context, selectedApps, "spotify", "com.spotify.music")
        lower.contains("open youtube music") || lower.contains("open yt music") -> openKnownApp(context, selectedApps, "youtube music", "com.google.android.apps.youtube.music")
        lower.startsWith("play music") || lower.startsWith("play song") || lower.startsWith("search music") -> {
            val term = q.substringAfter("music", q.substringAfter("song", "")).trim().removePrefix("by ")
            val player = selectedApps.firstOrNull { it.packageName == memory.defaultMusicPackage() }
                ?: selectedApps.firstOrNull { it.label.contains("youtube music", true) || it.label.contains("spotify", true) }
            if (term.isBlank()) {
                if (player != null && launchApp(context, player.packageName)) "Opening ${player.label}. Choose music to play."
                else { launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com"))); "Opening a music app or YouTube Music." }
            } else {
                val spotify = player?.label?.contains("spotify", true) == true
                val uri = if (spotify) Uri.parse("spotify:search:${Uri.encode(term)}") else Uri.parse("https://music.youtube.com/search?q=${Uri.encode(term)}")
                if (launchIntent(context, Intent(Intent.ACTION_VIEW, uri))) "Searching for '$term' in ${player?.label ?: "YouTube Music"}."
                else "Couldn't open a music search. Try opening your music app first."
            }
        }
        lower.contains("open dialer") || lower.contains("open phone") -> if (launchIntent(context, Intent(Intent.ACTION_DIAL))) "Opening the dialer." else "I couldn't open the dialer."
        lower.contains("open contacts") || lower.contains("show contacts") -> if (launchIntent(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))) "Opening Contacts." else "I couldn't open Contacts."
        lower.contains("open settings") || lower == "phone settings" -> if (launchIntent(context, Intent(Settings.ACTION_SETTINGS))) "Opening Android Settings." else "I couldn't open Settings."
        lower.startsWith("search for ") || lower.startsWith("google ") -> {
            val term = q.substringAfter("search for ", q.substringAfter("google ")).trim()
            if (launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(term)}")))) "Searching the web for $term." else "Couldn't open web search."
        }
        lower.startsWith("open website ") -> {
            val site = q.substringAfter("open website ").trim().let { if (it.startsWith("http")) it else "https://$it" }
            if (launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(site)))) "Opening $site." else "Couldn't open that website."
        }
        lower.startsWith("set an alarm for ") || lower.startsWith("set alarm for ") -> {
            val time = q.substringAfter("for ").trim()
            val parts = parseClockTime(time)
            if (parts == null) "Try ‘Set an alarm for 7:30 AM’."
            else {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, parts.first)
                    .putExtra(AlarmClock.EXTRA_MINUTES, parts.second)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "Set by TAURUS")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                if (launchIntent(context, intent)) "Opening the alarm setup for $time. Confirm it in your clock app." else "I couldn't open the alarm app."
            }
        }
        lower.startsWith("call ") -> {
            val number = q.substringAfter("call ").filter { it.isDigit() || it == '+' }
            if (number.length >= 5 && launchIntent(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))) "Opened the dialer for $number. Tap Call to place the call."
            else "For safety, TAURUS opens the dialer for you rather than placing a call automatically."
        }
        else -> null
    }
}

private fun openKnownApp(context: Context, apps: List<SavedApp>, name: String, packageName: String): String {
    val match = apps.firstOrNull { it.label.contains(name, true) }
    if (match != null) return if (launchApp(context, match.packageName)) "Opening ${match.label}." else "Couldn't open ${match.label}."
    if (launchApp(context, packageName)) return "Opening $name."
    return "$name doesn't appear to be installed, or Android doesn't allow it to be launched."
}

private fun launchApp(context: Context, packageName: String): Boolean = try {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (_: Exception) { false }

private fun launchIntent(context: Context, intent: Intent): Boolean = try {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
} catch (_: Exception) { false }

private fun parseRelativeReminder(text: String): Pair<String, Long>? {
    val regex = Regex("(?i)^remind me(?: to)?\\s+(.+?)\\s+in\\s+(\\d+)\\s*(minutes?|mins?|hours?|hrs?|seconds?|secs?)\\s*[.!]?$ ".trim())
    val match = regex.find(text.trim()) ?: return null
    val task = match.groupValues[1].trim().trimEnd('.', ' ')
    val amount = match.groupValues[2].toLongOrNull() ?: return null
    val unit = match.groupValues[3].lowercase(Locale.ROOT)
    val factor = when {
        unit.startsWith("hour") || unit.startsWith("hr") -> 3_600_000L
        unit.startsWith("sec") -> 1_000L
        else -> 60_000L
    }
    if (task.isBlank() || amount < 1 || amount > 525600) return null
    return task to (System.currentTimeMillis() + amount * factor)
}

private fun scheduleReminder(context: Context, id: Int, title: String, at: Long) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title)
    val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
}

private fun cancelReminder(context: Context, id: Int) {
    val intent = Intent(context, ReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
}

private fun parseClockTime(value: String): Pair<Int, Int>? {
    val match = Regex("(?i)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(value) ?: return null
    var hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
    val meridiem = match.groupValues[3].lowercase(Locale.ROOT)
    if (hour !in 1..12 || minute !in 0..59) return null
    if (meridiem == "pm" && hour < 12) hour += 12
    if (meridiem == "am" && hour == 12) hour = 0
    return hour to minute
}

private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1); inPreferredConfig = Bitmap.Config.ARGB_8888 }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun applyImageFilter(source: Bitmap, mode: Float): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val matrix = ColorMatrix()
    when (mode.toInt()) {
        1 -> matrix.setSaturation(0f)
        2 -> matrix.set(floatArrayOf(1.12f,0.08f,0f,0f,8f, 0.04f,1.02f,0f,0f,3f, 0f,0.02f,0.88f,0f,-3f, 0f,0f,0f,1f,0f))
        3 -> matrix.set(floatArrayOf(0.9f,0f,0.04f,0f,4f, 0f,1.03f,0f,0f,4f, 0.03f,0f,1.12f,0f,8f, 0f,0f,0f,1f,0f))
    }
    paint.colorFilter = ColorMatrixColorFilter(matrix)
    canvas.drawBitmap(source, 0f, 0f, paint)
    return result
}

private fun saveEditedImage(context: Context, bitmap: Bitmap): Uri? {
    val values = android.content.ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "TAURUS-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TAURUS")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) throw IllegalStateException("Image compression failed")
    }
    return uri
}
