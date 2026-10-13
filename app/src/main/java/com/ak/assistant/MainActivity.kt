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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private val Bg = Color(0xFF101014)
private val Panel = Color(0xFF1B1B22)
private val Accent = Color(0xFFB2A5FF)
private val Muted = Color(0xFFAAA9B7)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 300)
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) { AssistantScreen(onSpeak = { speak(it) }) } }
    }
    private fun speak(text: String) { tts?.speak(text.take(1500), TextToSpeech.QUEUE_FLUSH, null, "akai-reply") }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

data class ChatMessage(val text: String, val fromUser: Boolean)
data class DeviceApp(val label: String, val packageName: String)

@Composable
private fun AssistantScreen(onSpeak: (String) -> Unit) {
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
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageStatus by remember { mutableStateOf("") }
    var filter by remember { mutableFloatStateOf(0f) }
    var messages by remember { mutableStateOf(listOf(ChatMessage("Hi, I'm AK AI. I can chat, remember things on this phone, open apps, launch music searches, set alarms, and schedule reminders. Add a Gemini API key in Settings for general AI conversation.", false))) }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input = it }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedImage = uri; imageStatus = if (uri != null) "Image loaded. Choose a look and save a copy." else ""
        if (uri != null) scope.launch(Dispatchers.IO) {
            val bmp = try { decodeScaledBitmap(context, uri) } catch (_: Exception) { null }
            withContext(Dispatchers.Main) { imageBitmap = bmp; filter = 0f; if (bmp == null) imageStatus = "Couldn't load this image." }
        }
    }

    if (showSettings) {
        AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("AK AI settings") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gemini API key", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(), placeholder = { Text("Paste your API key") })
                Text("Cloud AI sends your prompts and any memories included with them to Google's API. Use a key from Google AI Studio; model availability and free quotas can change.", color = Muted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Speak replies", modifier = Modifier.weight(1f)); Switch(checked = speakReplies, onCheckedChange = { speakReplies = it; memory.setSpeakReplies(it) }) }
                TextButton(onClick = { memory.clearMemories(); messages = messages + ChatMessage("Saved memories cleared.", false) }) { Text("Clear saved memories") }
            } }, confirmButton = { TextButton(onClick = { memory.setApiKey(apiKey.trim()); showSettings = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } }, containerColor = Panel)
    }

    if (showAppPicker) {
        val installed = remember { installedApps(context) }
        var search by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAppPicker = false }, title = { Text("Add an app") }, text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true, placeholder = { Text("Search installed apps") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(installed.filter { it.label.contains(search, true) }.take(150), key = { it.packageName }) { app ->
                        val already = apps.any { it.packageName == app.packageName }
                        Row(Modifier.fillMaxWidth().clickable {
                            apps = if (already) apps.filterNot { it.packageName == app.packageName } else (apps + SavedApp(app.label, app.packageName)).distinctBy { it.packageName }
                            memory.saveSelectedApps(apps)
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(app.label, color = Color.White); Text(app.packageName, color = Muted, fontSize = 10.sp) }
                            if (already) Icon(Icons.Default.Check, contentDescription = "Added", tint = Accent) else Icon(Icons.Default.Add, contentDescription = "Add", tint = Muted)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showAppPicker = false }) { Text("Done") } }, containerColor = Panel)
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.SmartToy, contentDescription = null, tint = Bg) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) { Text("AK AI", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White); Text("Your personal assistant", fontSize = 12.sp, color = Muted) }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Chat", "Apps", "Reminders", "Images", "Memory").forEach { tab ->
                Surface(onClick = { screen = tab }, color = if (screen == tab) Accent else Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) {
                    Text(tab, color = if (screen == tab) Bg else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                            Column(Modifier.widthIn(max = 310.dp).background(if (msg.fromUser) Color(0xFF37304F) else Panel, RoundedCornerShape(18.dp)).padding(13.dp)) {
                                Text(msg.text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
                                if (!msg.fromUser) { Spacer(Modifier.height(4.dp)); TextButton(onClick = { onSpeak(msg.text) }) { Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Speak", fontSize = 12.sp) } }
                            }
                        }
                    }
                    if (busy) item { Text("AK AI is thinking…", color = Muted, modifier = Modifier.padding(8.dp)) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).background(Panel, RoundedCornerShape(28.dp)).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()); putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to AK AI") }
                        try { voiceLauncher.launch(intent) } catch (_: ActivityNotFoundException) { messages = messages + ChatMessage("Speech recognition isn't available on this phone.", false) }
                    }) { Icon(Icons.Default.Mic, contentDescription = "Speak", tint = Accent) }
                    BasicTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), decorationBox = { inner -> if (input.isEmpty()) Text("Ask AK anything…", color = Muted); inner() })
                    IconButton(enabled = input.isNotBlank() && !busy, onClick = {
                        val question = input.trim(); input = ""; messages = messages + ChatMessage(question, true); busy = true
                        scope.launch {
                            val actionResult = runAssistantAction(context, question, memory, apps, memory.defaultMusicPackage()) { text, at ->
                                val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                                memory.addReminder(id, text, at); scheduleReminder(context, id, text, at); reminders = memory.reminders()
                            }
                            val response = actionResult ?: if (memory.apiKey().isBlank()) "I can do phone actions, reminders and local memory without an API key. Add a Gemini API key in Settings for general AI chat." else GeminiClient.reply(memory.apiKey(), question, memory.allMemories())
                            messages = messages + ChatMessage(response, false); if (speakReplies) onSpeak(response); busy = false
                        }
                    }) { Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = if (input.isNotBlank()) Accent else Muted) }
                }
            }
            "Apps" -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Column(Modifier.weight(1f)) { Text("Your app library", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Choose which apps AK AI can launch", color = Muted, fontSize = 12.sp) }; FilledTonalButton(onClick = { showAppPicker = true }) { Icon(Icons.Default.Add, null); Text("Add app") } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Button(onClick = { if (defaultMusic.isNotBlank()) launchPackage(context, defaultMusic) else launchMusic(context, apps, "") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.MusicNote, null); Text("Open music") }
                    OutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.weight(1f)) { Text("Choose apps") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).clickable { launchPackage(context, app.packageName) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold); Text(if (defaultMusic == app.packageName) "Default music app · ${app.packageName}" else app.packageName, color = if (defaultMusic == app.packageName) Accent else Muted, fontSize = 10.sp) }
                            TextButton(onClick = { defaultMusic = app.packageName; memory.setDefaultMusicPackage(app.packageName) }) { Text(if (defaultMusic == app.packageName) "Default ✓" else "Set default", fontSize = 10.sp) }
                            IconButton(onClick = { apps = apps.filterNot { it.packageName == app.packageName }; if (defaultMusic == app.packageName) { defaultMusic = ""; memory.setDefaultMusicPackage("") }; memory.saveSelectedApps(apps) }) { Text("×", color = Muted, fontSize = 22.sp) }
                        }
                    }
                    if (apps.isEmpty()) item { Text("No apps added yet. Tap Add app to choose Spotify, YouTube Music, or any installed app.", color = Muted, modifier = Modifier.padding(16.dp)) }
                }
                Text("Commands: ‘Open Spotify’, ‘Open YouTube Music’, ‘Play music Coldplay’. Song playback depends on each app's supported links.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
            "Reminders" -> {
                Text("Reminders", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("Create a local reminder. AK AI can also understand ‘remind me to study in 20 minutes’ in chat.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(value = reminderTitle, onValueChange = { reminderTitle = it }, label = { Text("What should I remind you about?") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reminderMinutes, onValueChange = { reminderMinutes = it.filter(Char::isDigit).take(5) }, label = { Text("Remind me in how many minutes?") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val mins = reminderMinutes.toLongOrNull()
                    if (reminderTitle.isNotBlank() && mins != null && mins > 0) {
                        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(); val at = System.currentTimeMillis() + mins * 60_000L
                        memory.addReminder(id, reminderTitle.trim(), at); scheduleReminder(context, id, reminderTitle.trim(), at); reminders = memory.reminders(); reminderTitle = ""
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Schedule reminder") }
                Text("Scheduled", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reminders, key = { it.id }) { r ->
                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(r.title, color = Color.White); Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(r.at), color = Muted, fontSize = 11.sp) }
                            TextButton(onClick = { cancelReminder(context, r.id); memory.removeReminder(r.id); reminders = memory.reminders() }) { Text("Cancel") }
                        }
                    }
                    if (reminders.isEmpty()) item { Text("No scheduled reminders.", color = Muted) }
                }
            }
            "Images" -> {
                Text("Image studio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("Select a photo, apply a quick offline look, and save an edited copy. Generative AI editing will be a separate provider integration.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = { imagePicker.launch(ActivityResultContracts.PickVisualMedia.ImageOnly) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Choose photo") }
                if (imageBitmap != null) {
                    AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, update = { view -> view.setImageBitmap(applyImageFilter(imageBitmap!!, filter)) }, modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                    Text("Look", color = Color.White)
                    Slider(value = filter, onValueChange = { filter = it }, valueRange = 0f..3f, steps = 2)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Original", color = Muted, fontSize = 11.sp); Text("B&W", color = Muted, fontSize = 11.sp); Text("Warm", color = Muted, fontSize = 11.sp); Text("Cool", color = Muted, fontSize = 11.sp) }
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val output = try { saveEditedImage(context, applyImageFilter(imageBitmap!!, filter)) } catch (_: Exception) { null }
                            withContext(Dispatchers.Main) { imageStatus = if (output != null) "Edited copy saved to Pictures/AK AI." else "Couldn't save the edited image." }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save edited copy") }
                } else { Spacer(Modifier.weight(1f)); Text("Your selected photo will appear here.", color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp)) }
                if (imageStatus.isNotBlank()) Text(imageStatus, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            else -> {
                Text("On-device memory", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("Only memories you explicitly save are stored here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    val saved = memory.allMemories().lines().filter { it.isNotBlank() }
                    items(saved) { line -> Text(line, color = Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)) }
                    if (saved.isEmpty()) item { Text("No memories saved yet. In Chat, say ‘Remember that …’", color = Muted) }
                }
                OutlinedButton(onClick = { memory.clearMemories() }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Clear all memories") }
            }
        }
    }
}

@Composable private fun QuickAction(label: String, action: () -> Unit) { Surface(onClick = action, color = Panel, shape = RoundedCornerShape(18.dp)) { Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) } }

private fun installedApps(context: Context): List<DeviceApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION") val resolves = context.packageManager.queryIntentActivities(intent, 0)
    return resolves.mapNotNull { ri -> val ai = ri.activityInfo ?: return@mapNotNull null; DeviceApp(ri.loadLabel(context.packageManager).toString(), ai.packageName) }
        .distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private suspend fun runAssistantAction(context: Context, text: String, memory: LocalMemory, selectedApps: List<SavedApp>, defaultMusicPackage: String, createReminder: (String, Long) -> Unit): String? {
    val q = text.trim(); val lower = q.lowercase(Locale.getDefault())
    return when {
        lower.startsWith("remember that ") || lower.startsWith("remember ") || lower.startsWith("save this:") -> {
            val value = q.substringAfter("remember that ", q.substringAfter("remember ", q.substringAfter("save this:", ""))).trim()
            if (value.isBlank()) "Tell me what you'd like me to remember." else { memory.addMemory(value); "Saved to your on-device memory: $value" }
        }
        lower.contains("what do you remember") || lower == "my memories" || lower.contains("show my memories") -> { val all = memory.allMemories(); if (all.isBlank()) "You haven't saved any memories yet. Say ‘Remember that …’ to save one." else "Here are your saved memories:\n$all" }
        lower.startsWith("forget all memories") || lower.startsWith("clear my memories") -> { memory.clearMemories(); "Done. I've cleared your saved memories." }
        lower.startsWith("remind me to ") || lower.startsWith("remind me ") -> {
            val parsed = parseReminder(q)
            if (parsed == null) "I can schedule reminders like ‘Remind me to study in 20 minutes’ or ‘Remind me to call home in 2 hours’." else { createReminder(parsed.first, parsed.second); "Reminder scheduled: ${parsed.first} — ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(parsed.second)}." }
        }
        lower.startsWith("open app ") -> {
            val name = q.substringAfter("open app ").trim(); val match = selectedApps.firstOrNull { it.label.equals(name, true) } ?: selectedApps.firstOrNull { it.label.contains(name, true) }
            if (match != null) launchPackage(context, match.packageName).let { if (it) "Opening ${match.label}." else "I couldn't open ${match.label}." } else "I couldn't find '$name' in your selected apps. Open Apps and tap Add app first."
        }
        lower.contains("open spotify") -> openKnownOrSearch(context, selectedApps, listOf("spotify"), "com.spotify.music", "spotify", "")
        lower.contains("open youtube music") || lower.contains("open yt music") -> openKnownOrSearch(context, selectedApps, listOf("youtube music", "yt music"), "com.google.android.apps.youtube.music", "ytmusic", "")
        lower.startsWith("set ") && (lower.contains(" as music app") || lower.contains(" as default music")) -> {
            val name = q.substringAfter("set ").substringBefore(" as ").trim()
            val app = selectedApps.firstOrNull { it.label.equals(name, true) } ?: selectedApps.firstOrNull { it.label.contains(name, true) }
            if (app == null) "I couldn't find '$name' in your app library. Add it first in Apps." else { memory.setDefaultMusicPackage(app.packageName); "${app.label} is now your default music app." }
        }
        lower.startsWith("play music") || lower.startsWith("play song") || lower.startsWith("search music") -> {
            val term = q.substringAfter("music", q.substringAfter("song", "")).trim().removePrefix("by ")
            val player = selectedApps.firstOrNull { it.packageName == defaultMusicPackage }
                ?: selectedApps.firstOrNull { it.label.contains("youtube music", true) || it.label.contains("spotify", true) }
            if (term.isBlank()) { if (player != null) { launchPackage(context, player.packageName); "Opening ${player.label}. Choose music to play." } else { launchMusic(context, selectedApps, ""); "Opening your available music app. Add Spotify or YouTube Music in Apps to choose a preferred player." } }
            else {
                val target = player?.label?.lowercase(Locale.ROOT) ?: "youtube music"
                val uri = if (target.contains("spotify")) Uri.parse("spotify:search:${Uri.encode(term)}") else Uri.parse("https://music.youtube.com/search?q=${Uri.encode(term)}")
                launchIntent(context, Intent(Intent.ACTION_VIEW, uri), "I couldn't open a music search. Try opening your music app first.", "Searching for '$term' in ${player?.label ?: "YouTube Music"}. If the app doesn't support this link, I'll open its normal screen instead.")
            }
        }
        lower.contains("open dialer") || lower.contains("open phone") -> launchIntent(context, Intent(Intent.ACTION_DIAL), "I couldn't open the dialer.", "Opening the dialer.")
        lower.contains("open contacts") || lower.contains("show contacts") -> launchIntent(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI), "I couldn't open Contacts.", "Opening Contacts.")
        lower.contains("open settings") || lower == "phone settings" -> launchIntent(context, Intent(Settings.ACTION_SETTINGS), "I couldn't open Settings.", "Opening Android Settings.")
        lower.startsWith("search for ") || lower.startsWith("google ") -> { val term = q.substringAfter("search for ", q.substringAfter("google ")).trim(); launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(term))), "I couldn't open search.", "Searching the web for $term.") }
        lower.startsWith("open website ") -> { val site = q.substringAfter("open website ").trim().let { if (it.startsWith("http")) it else "https://$it" }; launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(site)), "I couldn't open that website.", "Opening $site.") }
        lower.startsWith("set an alarm for ") || lower.startsWith("set alarm for ") -> { val time = q.substringAfter("for ").trim(); val parts = parseTime(time); if (parts == null) "I couldn't understand that time. Try ‘Set an alarm for 7:30 AM’." else { val intent = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, parts.first).putExtra(AlarmClock.EXTRA_MINUTES, parts.second).putExtra(AlarmClock.EXTRA_MESSAGE, "Set by AK AI").putExtra(AlarmClock.EXTRA_SKIP_UI, false); launchIntent(context, intent, "I couldn't open the alarm app.", "Opening the alarm setup for $time. Please confirm it in your clock app.") } }
        lower.startsWith("call ") -> { val number = q.substringAfter("call ").filter { it.isDigit() || it == '+' }; if (number.length >= 5) launchIntent(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")), "I couldn't open the dialer.", "Opened the dialer for $number. Tap Call to place the call.") else "For safety, I won't place a call automatically. Give me a phone number or say ‘Open dialer’." }
        else -> null
    }
}

private fun openKnownOrSearch(context: Context, apps: List<SavedApp>, labels: List<String>, packageName: String, fallback: String, query: String): String {
    val match = apps.firstOrNull { app -> labels.any { app.label.contains(it, true) } }
    if (match != null) return if (launchPackage(context, match.packageName)) "Opening ${match.label}." else "I couldn't open ${match.label}."
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) return try { context.startActivity(intent); "Opening ${labels.first()}" } catch (_: Exception) { "Couldn't open ${labels.first()}." }
    return if (fallback == "spotify") "Spotify doesn't appear to be installed. Add it from Apps if it is installed." else "YouTube Music doesn't appear to be installed. Add it from Apps if it is installed."
}
private fun launchMusic(context: Context, apps: List<SavedApp>, query: String): Boolean {
    val app = apps.firstOrNull { it.packageName == LocalMemory(context).defaultMusicPackage() }
        ?: apps.firstOrNull { it.label.contains("youtube music", true) } ?: apps.firstOrNull { it.label.contains("spotify", true) } ?: apps.firstOrNull { it.label.contains("music", true) }
    return if (app != null) launchPackage(context, app.packageName) else launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com")), "No music app or browser available.", "Opening YouTube Music.") == "Opening YouTube Music."
}
private fun launchPackage(context: Context, packageName: String): Boolean {
    return try {
        val i = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); true
    } catch (_: Exception) { false }
}
private fun launchIntent(context: Context, intent: Intent, fail: String, success: String): String = try { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); success } catch (_: Exception) { fail }

private fun parseReminder(text: String): Pair<String, Long>? {
    val q = text.trim()
    val match = Regex("(?i)^remind me(?: to)?\\s+(.+?)\\s+in\\s+(\\d+)\\s*(minutes?|mins?|hours?|hrs?|seconds?|secs?)\\s*[.!]?$ ".trim()).find(q) ?: return null
    val task = match.groupValues[1].trim().trimEnd('.', ' '); val amount = match.groupValues[2].toLongOrNull() ?: return null
    val unit = match.groupValues[3].lowercase(); val factor = when { unit.startsWith("hour") || unit.startsWith("hr") -> 3_600_000L; unit.startsWith("sec") -> 1_000L; else -> 60_000L }
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
private fun parseTime(value: String): Pair<Int, Int>? {
    val match = Regex("(?i)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(value) ?: return null
    var hour = match.groupValues[1].toIntOrNull() ?: return null; val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null; val meridiem = match.groupValues[3].lowercase()
    if (hour !in 1..12 || minute !in 0..59) return null
    if (meridiem == "pm" && hour < 12) hour += 12; if (meridiem == "am" && hour == 12) hour = 0
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
    val bmp = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val matrix = ColorMatrix()
    when (mode.toInt()) {
        1 -> matrix.setSaturation(0f)
        2 -> matrix.set(floatArrayOf(1.12f,0.08f,0f,0f,8f, 0.04f,1.02f,0f,0f,3f, 0f,0.02f,0.88f,0f,-3f, 0f,0f,0f,1f,0f))
        3 -> matrix.set(floatArrayOf(0.9f,0f,0.04f,0f,4f, 0f,1.03f,0f,0f,4f, 0.03f,0f,1.12f,0f,8f, 0f,0f,0f,1f,0f))
    }
    paint.colorFilter = ColorMatrixColorFilter(matrix); canvas.drawBitmap(source, 0f, 0f, paint)
    return bmp
}
private fun saveEditedImage(context: Context, bitmap: Bitmap, ): Uri? {
    val values = android.content.ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "AKAI-${System.currentTimeMillis()}.jpg"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AK AI") }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { out -> if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) throw IllegalStateException("Image compression failed") }
    return uri
}
