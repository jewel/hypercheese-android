package com.hypercheese.upload

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hypercheese.upload.ui.theme.HypercheeseUploadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("HyperCheese", Context.MODE_PRIVATE)
        val prevAuth = sharedPref.getBoolean("authenticated", false)
        if(prevAuth) {
            SERVER = sharedPref.getString("server", "")
            TOKEN = sharedPref.getString("token", "")
        }
        val prevPerm = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

        setContent {
            HypercheeseUploadTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(prevAuth, prevPerm)
                }
            }
        }
    }
}

@Composable
fun App(prevAuth: Boolean, prevPerm: Boolean) {
    var authenticated by remember { mutableStateOf(prevAuth) }
    var hasPermission by remember { mutableStateOf(prevPerm) }

    if (!authenticated)
        LoginPage {
            authenticated = true
        }
    else if(!hasPermission)
        PermissionPage {
            hasPermission = true
        }
    else
        MainPage()
}

