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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionPage(permissionGranted: () -> Unit)
{
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if(isGranted)
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
                requestPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        ) {
            Text("Grant Permission")
        }
    }
}
