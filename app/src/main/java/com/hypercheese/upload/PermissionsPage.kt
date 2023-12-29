package com.hypercheese.upload

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionPage(permissionGranted: () -> Unit)
{
    var target by remember { mutableStateOf("") }
    var imagePermission by remember { mutableStateOf(false) }
    var videoPermission by remember { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if(target == "images")
            imagePermission = isGranted
        if(target == "video")
            videoPermission = isGranted
    }

    LaunchedEffect(imagePermission, videoPermission) {
        if(imagePermission && videoPermission)
            permissionGranted()
    }

    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Banner("Upload")
        Spacer(Modifier.height(16.dp))
        Text("In order to upload your photos and videos to the HyperCheese server, this app needs permission to access your camera roll.")
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if(!imagePermission) {
                    target = "images"
                    requestPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
                else if(!videoPermission) {
                    target = "video"
                    requestPermission.launch(Manifest.permission.READ_MEDIA_VIDEO)
                }
            }
        ) {
            Text("Grant Permission")
        }
    }
}
