package com.hypercheese.upload

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// A version number to send so that the server can refuse connections from old software
val API_VERSION = "1.0"
var SERVER : String? = null
var TOKEN : String? = null

fun postJSON(uri: String, input: JSONArray): JSONArray {
    // TODO Check if on metered connection if this was launched by the periodic check
    var connection : HttpURLConnection? = null
    try {
        val url = URL("$SERVER/$uri")
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $TOKEN")
        connection.setRequestProperty("X-API-Version", API_VERSION)
        connection.doOutput = true

        // FIXME compress this with gzip or brotli
        connection.outputStream.write(input.toString().toByteArray())

        Log.d("HyperCheese", "sending request")

        if (connection.responseCode == 200)
            return JSONArray(connection.inputStream.bufferedReader().readText())

        var body = connection.errorStream.bufferedReader().readText()

        if(connection.getHeaderField("Content-Type").startsWith("text/plain"))
            throw Exception("Server error: $body")

        throw Exception("Server error: ${connection.responseCode}")
    }
    finally {
        connection?.disconnect()
    }
}

suspend fun uploadFiles(context: Context, files: JSONArray, updateStatus: (Float, String) -> Unit, isActive: () -> Boolean) {
    val total = addSizes(files)
    var uploaded = 0L
    var uploadedFiles = 0

    for(i in 0 until files.length()) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if(connectivityManager.isActiveNetworkMetered()) {
            delay(5000)
            updateStatus(0f, "Error: Not on WiFi or WiFi is metered")
            // TODO we really would like to queue this file to retry later
            continue;
        }

        val info = files.getJSONObject(i)
        val path = info.getString("path")
        val file = File(path)
        val input = file.inputStream()
        val url = URL("$SERVER/files")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.setRequestProperty("Authorization", "Bearer $TOKEN")
        connection.setRequestProperty("X-Path", path)
        connection.setRequestProperty("X-MTime", file.lastModified().toString())
        connection.setRequestProperty("X-API-Version", API_VERSION)
        connection.setFixedLengthStreamingMode(file.length())
        val out = BufferedOutputStream(connection.outputStream)
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while ( input.read (buffer).also { bytesRead = it } != -1) {
            out.write(buffer, 0, bytesRead)
            uploaded += bytesRead
            val progress = uploaded.toFloat() / total.toFloat()
            updateStatus(progress, "Uploaded ${pluralize(uploadedFiles, "file")}, ${humanize(uploaded)} of ${humanize(total)}")
            if(!isActive()) {
                connection.disconnect()
                throw Exception("Cancelled")
            }
        }
        out.flush()
        input.close()
        out.close()
        if (connection.responseCode == 200) {
            uploadedFiles++
        } else {
            // TODO handle error
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

@Throws(Exception::class)
fun authenticate(url: String, username: String, password: String): String {
    var connection : HttpURLConnection? = null
    try {
        val url = URL("$url/files/authenticate")
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-API-Version", API_VERSION)
        connection.doOutput = true

        val obj = JSONObject()
        obj.put("username", username)
        obj.put("password", password)

        connection.outputStream.write(obj.toString().toByteArray())

        if (connection.responseCode == 401) {
            throw Exception("Invalid username or password")
        }
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

fun addSizes(files: JSONArray) : Long {
    var total = 0L
    for( i in 0 until files.length() ) {
        val row = files.getJSONObject(i)
        val path = row.getString("path")
        val file = File(path)
        if (file.exists() && file.isFile)
            total += file.length()
    }
    return total
}
