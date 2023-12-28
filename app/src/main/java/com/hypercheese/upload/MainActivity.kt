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
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HypercheeseUploadTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Greeting("Android")
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

@Composable
fun Greeting(name: String) {

    Column {
        var statusMessage by remember { mutableStateOf("Status") }
        var currentProgress by remember { mutableStateOf(0.0f) }
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current

        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    statusMessage = "Scanning"
                    val files = scanForFiles(context) { msg ->
                        statusMessage = msg
                    }
                    statusMessage = "Submitting Manifest"
                    val toHash = submitManifest(files)
                    val hashes = hashFiles(toHash) { msg ->
                        statusMessage = msg
                    }
                    statusMessage = "Submitting Hashes"
                    val toUpload = submitHashes(hashes)
                    uploadFiles(toUpload) { msg ->
                        statusMessage = msg
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
val server = "http://192.168.86.61:3000"

suspend fun scanForFiles(context: Context, updateStatus: (String) -> Unit): JSONArray {
    updateStatus("Scanning")

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
            updateStatus("Scanning $count of $total")
            res.put(obj)
        }
    }
    return res
}

suspend fun submitManifest(files: JSONArray): JSONArray {
    var connection : HttpURLConnection? = null
    try {
        val url = URL("$server/files/manifest")
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // FIXME compress this with gzip or brotli
        connection.outputStream.write(files.toString().toByteArray())

        if (connection.responseCode == 200) {
            return JSONArray(connection.inputStream.bufferedReader().readText())
        }
        throw Exception("Server error")
    }
    finally {
        connection?.disconnect()
    }
}

suspend fun hashFiles(files: JSONArray, updateStatus: (String) -> Unit): JSONArray {
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

suspend fun submitHashes(files: JSONArray): JSONArray {
    var connection : HttpURLConnection? = null
    try {
        val url = URL("$server/files/hashes")
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        connection.outputStream.write(files.toString().toByteArray())

        if (connection.responseCode == 200) {
            return JSONArray(connection.inputStream.bufferedReader().readText())
        }
        throw Exception("Server error")
    }
    finally {
        connection?.disconnect()
    }
}

suspend fun uploadFiles(files: JSONArray, updateStatus: (String) -> Unit) {
    for(i in 0 until files.length()) {
        val info = files.getJSONObject(i)
        val path = info.getString("path")
        val file = File(path)
        val input = file.inputStream()
        val url = URL("$server/files")
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

    @Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HypercheeseUploadTheme {
        Greeting("Android")
    }
}