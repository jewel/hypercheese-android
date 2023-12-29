package com.hypercheese.upload

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp


@Composable
fun Banner(title: String)
{
    Row {
        Image(
            painterResource(id = R.drawable.logo),
            contentDescription = "Logo",
            modifier = Modifier.width(36.dp)
        )
        Text(
            text = "HyperCheese $title",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start=8.dp),
        )
    }
}
