package com.hypercheese.upload

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LoginPage(setAuthenticated: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Banner("Login")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            label = { Text("Server URL") },
            value = url,
            onValueChange = { url = it },
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        url = cleanURL(url)
                    }
                }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            label = { Text("Username") },
            value = username,
            onValueChange = { username = it },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            label = { Text("Password") },
            value = password,
            onValueChange = { password = it },
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !processing,
            onClick = {
                processing = true
                status = "Authenticating..."
                coroutineScope.launch(Dispatchers.IO) {
                    var token: String? = null
                    try {
                        token = authenticate(url, username, password)
                    } catch (e: Exception) {
                        status = "${e.message} (${e.javaClass.simpleName})"
                    }
                    processing = false
                    if (token != null) {
                        saveLogin(context, url, token)
                        status = "Logged in!"
                        setAuthenticated()
                    }
                }
            },
        ) {
            Text("Login")
        }
        if(status != "") {
            Spacer(Modifier.height(8.dp))
            Text(status)
            if(processing) {
                LinearProgressIndicator()
            }
        }
    }
}

