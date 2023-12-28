package com.hypercheese.upload

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.hypercheese.upload.ui.theme.HypercheeseUploadTheme
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import android.Manifest
import android.graphics.Outline
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

var SERVER : String? = null
var TOKEN : String? = null

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("HyperCheese", Context.MODE_PRIVATE)
        val prevAuth = sharedPref.getBoolean("authenticated", false)
        if(prevAuth) {
            SERVER = sharedPref.getString("server", "")
            TOKEN = sharedPref.getString("token", "")
        }

        setContent {
            HypercheeseUploadTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val authenticated = remember { mutableStateOf(prevAuth) }
                    if(!authenticated.value)
                        LoginPage() {
                            authenticated.value = true
                        }
                    else
                        MainPage()
                }
            }
        }

        if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
                1001
            )
        }
    }
}

// A version number to send so that the server can refuse connections from old software
val API_VERSION = 1

@Composable
fun LoginPage(setAuthenticated: () -> Unit) {
    val url = remember { mutableStateOf("") }
    val username = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val processing = remember { mutableStateOf(false) }
    val status = remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            Image(
                painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.width(36.dp)
            )
            Text(
                text = "HyperCheese Login",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start=8.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            label = { Text("Server URL") },
            value = url.value,
            onValueChange = { url.value = it },
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if(!focusState.isFocused) {
                        url.value = cleanURL(url.value)
                    }
                }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            label = { Text("Username") },
            value = username.value,
            onValueChange = { username.value = it },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            label = { Text("Password") },
            value = password.value,
            onValueChange = { password.value = it },
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !processing.value,
            onClick = {
                processing.value = true
                status.value = "Authenticating..."
                coroutineScope.launch(Dispatchers.IO) {
                    var token: String? = null
                    try {
                        token = authenticate(url.value, username.value, password.value)
                    } catch (e: Exception) {
                        status.value = e.toString()
                    }
                    processing.value = false
                    if (token != null) {
                        saveLogin(context, url.value, token)
                        status.value = "Logged in!"
                    }
                }
            },
        ) {
            Text("Login")
        }
        if(status.value != "") {
            Spacer(Modifier.height(8.dp))
            Text(status.value)
            if(processing.value) {
                LinearProgressIndicator()
            }
        }
    }
}

fun saveLogin(context: Context, url: String, token: String)
{
    val sharedPrefs = context.getSharedPreferences("HyperCheese", Context.MODE_PRIVATE)
    with(sharedPrefs.edit()) {
        putBoolean("authenticated", true)
        putString("server", url)
        putString("token", token)
        apply()
    }
    SERVER = url
    TOKEN = token
}

fun cleanURL(input: String) : String {
    if(input.length == 0)
        return ""
    val url = if(!input.contains("://")) "https://$input" else input
    return url.removeSuffix("/")
}

fun authenticate(url: String, username: String, password: String): String {
    var connection : HttpURLConnection? = null
    try {
        val url = URL("$url/files/authenticate")
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val obj = JSONObject()
        obj.put("username", username)
        obj.put("password", password)
        obj.put("version", API_VERSION)

        connection.outputStream.write(obj.toString().toByteArray())

        if (connection.responseCode == 200) {
            val res = JSONObject(connection.inputStream.bufferedReader().readText())
            return res.getString("token")
        }
        throw Exception("Server error: ${connection.responseCode}")
    }
    finally {
        connection?.disconnect()
    }
}

@Composable
fun MainPage() {
    var statusMessage by remember { mutableStateOf("Status") }
    var currentProgress by remember { mutableStateOf(0.0f) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column {
        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        statusMessage = "Scanning"
                        val files = scanForFiles(context)

                        statusMessage = "Submitting Manifest"
                        val toHash = postJSON("files/manifest", files)
                        val hashes = hashFiles(toHash) { msg ->
                            statusMessage = msg
                        }

                        statusMessage = "Submitting Hashes"
                        val toUpload = postJSON("files/hashes", hashes)

                        statusMessage = "Uploading"
                        uploadFiles(toUpload) { msg ->
                            statusMessage = msg
                        }
                    }
                    catch(e: Exception) {
                        statusMessage = e.toString()
                    }
                }
                currentProgress += 0.1f
            }
        ) {
            Text("Refresh")
        }

        Spacer(Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = currentProgress
        )

        Spacer(Modifier.height(16.dp))

        Text(text = statusMessage)
    }
}

fun scanForFiles(context: Context): JSONArray {
    val res = JSONArray()
    val projection = arrayOf(
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.SIZE
    )
    val cursor = context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        null,
        null,
        null
    )
    cursor?.use {
        val iData = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
        val iDate = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val iSize = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        var count = 0
        val total = cursor.count
        while(it.moveToNext()) {
            count++
            val obj = JSONObject()
            obj.put("path", it.getString(iData))
            obj.put("mtime", it.getLong(iDate))
            obj.put("size", it.getLong(iSize))
            res.put(obj)
        }
    }
    return res
}

fun postJSON(uri: String, input: JSONArray): JSONArray {
    var connection : HttpURLConnection? = null
    try {
        val url = URL("$SERVER/$uri")
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // FIXME compress this with gzip or brotli
        connection.outputStream.write(input.toString().toByteArray())

        if (connection.responseCode == 200) {
            return JSONArray(connection.inputStream.bufferedReader().readText())
        }
        throw Exception("Server error: ${connection.responseCode}")
    }
    finally {
        connection?.disconnect()
    }
}

fun hashFiles(files: JSONArray, updateStatus: (String) -> Unit): JSONArray {
    val res = JSONArray()
    for(i in 0 until files.length()) {
        val info = files.getJSONObject(i)
        val path = info.getString("path")
        val digest = MessageDigest.getInstance("SHA256")
        val file = File(path)
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val obj = JSONObject()
        obj.put("path", path)
        obj.put("sha256", hash)
        res.put(obj)
    }
    return res
}
fun uploadFiles(files: JSONArray, updateStatus: (String) -> Unit) {
    for(i in 0 until files.length()) {
        val info = files.getJSONObject(i)
        val path = info.getString("path")
        val file = File(path)
        updateStatus("Uploading $path")
        val input = file.inputStream()
        val url = URL("$SERVER/files")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.setRequestProperty("X-Path", path)
        connection.setFixedLengthStreamingMode(file.length())
        val out = BufferedOutputStream(connection.outputStream)
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while ( input.read (buffer).also { bytesRead = it } != -1) {
            out.write(buffer, 0, bytesRead)
        }
        out.flush()
        input.close()
        out.close()
        if (connection.getResponseCode() == 200) {
            continue
        } else {
            // TODO handle error
        }
    }
}