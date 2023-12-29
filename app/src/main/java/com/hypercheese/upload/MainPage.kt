package com.hypercheese.upload

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

@Composable
fun MainPage() {
    var isRefreshing by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("") }
    var hashStatus by remember { mutableStateOf("") }
    var hashProgress by remember { mutableStateOf(0f) }
    var uploadStatus by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableStateOf(0f) }
    var toUpload by remember { mutableStateOf(JSONArray()) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var wifiOnly by rememberSharedPreference("wifi_only", true)

    val doRefresh = {
        if( !isRefreshing ) {
            coroutineScope.launch(Dispatchers.IO) {
                isRefreshing = true
                try {
                    toUpload = refresh { s, p, h ->
                        scanStatus = s
                        hashProgress = p
                        hashStatus = h
                    }
                }
                catch( e: Exception ) {
                    // Assume the problem was with the hashing
                    hashStatus = "${e.message} (${e.javaClass.simpleName})"
                }
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(key1 = Unit) {
        doRefresh()
    }

    Column (
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ){
        Banner("Upload")
        Spacer(Modifier.height(16.dp))
        Row {
            Column {
                Text(scanStatus)
                Spacer(Modifier.height(8.dp))
                Text(hashStatus)
            }
            if( !isRefreshing ) {
                IconButton(
                    enabled = !isRefreshing && !isUploading,
                    onClick = doRefresh
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Rescan files"
                    )
                }
            }
        }
        if( isRefreshing )
            LinearProgressIndicator(progress = hashProgress)

        Spacer(Modifier.height(16.dp))

        Row {
            Checkbox(
                checked = wifiOnly,
                onCheckedChange = { isChecked ->
                    wifiOnly = isChecked
                },
            )
            Text("Only upload on WiFi")
        }

        if( !isUploading ) {
            Button(
                enabled = !isRefreshing && toUpload.length() > 0,
                onClick = {
                   uploadJob = coroutineScope.launch(Dispatchers.IO) {
                        isUploading = true
                        try {
                            uploadFiles(context, toUpload,
                                { p, m ->
                                    uploadProgress = p
                                    uploadStatus = m
                                },
                                {
                                    isActive
                                }
                            )
                            toUpload = JSONArray()
                            hashStatus = buildFinalHashStatus(toUpload)
                        }
                        catch(e: Exception) {
                            uploadStatus = "${e.message} (${e.javaClass.simpleName})"
                        }
                        isUploading = false
                    }
                }
            ) {
                Text("Upload")
            }
        }
        else {
            OutlinedButton(
                onClick = {
                    uploadJob?.cancel()
                    isUploading = false
                },
            ) {
                Text("Cancel")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(uploadStatus)
        if( isUploading ) {
            LinearProgressIndicator(
                progress = uploadProgress
            )
        }
    }
}

@Composable
fun rememberSharedPreference (key: String, default: String) : MutableState<String?> {
    val context = LocalContext.current
    val memorable = remember {
        val prefs = context.getSharedPreferences("HyperCheese", Context.MODE_PRIVATE)
        val value = prefs.getString(key, default)
        mutableStateOf(value)
    }
    LaunchedEffect(memorable.value) {
        val prefs = context.getSharedPreferences("HyperCheese", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString(key, memorable.value)
        }
    }
    return memorable
}

@Composable
fun rememberSharedPreference (key: String, default: Boolean) : MutableState<Boolean> {
    val context = LocalContext.current
    val memorable = remember {
        val prefs = context.getSharedPreferences("HyperCheese", Context.MODE_PRIVATE)
        val value = prefs.getBoolean(key, default)
        mutableStateOf(value)
    }
    LaunchedEffect(memorable.value) {
        val prefs = context.getSharedPreferences("HyperCheese", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(key, memorable.value)
        }
    }
    return memorable
}

fun readMedia(context: Context, contentUri: Uri, projection: Array<String>, result: JSONArray) {
    val selection = "${projection[0]} LIKE ?"

    val cursor = context.contentResolver.query(
        contentUri,
        projection,
        null,
        null,
        null
    )
    cursor?.use {
        val iData = it.getColumnIndexOrThrow(projection[0])
        val iDate = it.getColumnIndexOrThrow(projection[1])
        val iSize = it.getColumnIndexOrThrow(projection[2])

        while (it.moveToNext()) {
            val obj = JSONObject()
            obj.put("path", it.getString(iData))
            obj.put("mtime", it.getLong(iDate))
            obj.put("size", it.getLong(iSize))
            result.put(obj)
        }
    }
}

fun scanFilesOld(context: Context) : JSONArray {
    val videoProjection = arrayOf(
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.SIZE
    )
    val videoUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    val imageProjection = arrayOf(
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE
    )
    val imageUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    val result = JSONArray()
    readMedia(context, videoUri, videoProjection, result)
    readMedia(context, imageUri, imageProjection, result)
    return result
}

fun searchDirectory(dir: File, list: JSONArray) {
    val files = dir.listFiles()
    files?.forEach { file ->
        if (file.isDirectory) {
            searchDirectory(file, list)
        } else {
            val obj = JSONObject()
            obj.put("path", file.canonicalPath)
            obj.put("mtime", file.lastModified())
            obj.put("size", file.length())
            list.put(obj)
        }
    }
}

fun scanFiles() : JSONArray {
    val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val res = JSONArray()
    searchDirectory(dcim, res)
    return res
}


fun pluralize(count: Int, name: String) : String {
    if(count == 1)
        return "$count $name"
    else
        return "$count ${name}s"
}

fun humanize(bytes: Long) : String {
    var amount = bytes
    if( amount < 1024 )
        return "$amount bytes"
    amount /= 1024
    if( amount < 1024 )
        return "$amount KB"
    amount /= 1024
    if( amount < 1024 )
        return "$amount MB"
    amount /= 1024
    if( amount < 1024 )
        return "$amount GB"
    amount /= 1024
    return "$amount TB"
}

fun buildScanStatus(files: JSONArray) : String {
    var count = files.length()
    var size = 0L
    for( i in 0 until files.length() ) {
        val row = files.getJSONObject(i)
        size += row.getLong("size")
    }
    return "On your device: ${pluralize(count, "file")}, ${humanize(size)}"
}

fun buildFinalHashStatus(files: JSONArray) : String {
    var count = files.length()
    var size = 0L
    for( i in 0 until files.length() ) {
        val row = files.getJSONObject(i)
        val path = row.getString("path")

        // Technically we should already know the file size but we're going to stat it again here
        // because it's less complicated that way
        val file = File(path)

        // Ignore problems here, the actual upload process will take care of them
        if(!file.exists() || !file.isFile)
            continue

        size += file.length()
    }
    return "To upload: ${pluralize(count, "file")}, ${humanize(size)}"
}

fun refresh(updateStatus: (String, Float, String) -> Unit) : JSONArray {
    updateStatus("Scanning", 0f, "")
    val files = scanFiles()
    val scanStatus = buildScanStatus(files)
    updateStatus(scanStatus, 0.01f, "Conferring with Server")

    val toHash = postJSON("files/manifest", files)

    val hashes = hashFiles(toHash) { p, msg ->
        updateStatus(scanStatus, p, msg)
    }

    updateStatus(scanStatus, 0.99f, "Conferring with Server some more")
    val toUpload = postJSON("files/hashes", hashes)
    updateStatus(scanStatus, 1f, buildFinalHashStatus(toUpload))
    return toUpload
}

fun hashFiles(files: JSONArray, updateStatus: (Float, String) -> Unit): JSONArray {
    val res = JSONArray()
    var total = addSizes(files)

    var digested = 0L

    for(i in 0 until files.length()) {
        val row = files.getJSONObject(i)
        val path = row.getString("path")
        val digest = MessageDigest.getInstance("SHA256")
        val file = File(path)
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                digested += bytesRead
                val progress = digested.toFloat() / total.toFloat()
                updateStatus(progress, "Hashing, ${humanize(digested)} of ${humanize(total)}")
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
