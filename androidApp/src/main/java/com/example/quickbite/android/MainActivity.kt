package com.example.quickbite.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.quickbite.navigation.AppNavHost
import com.example.quickbite.android.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
                MyApplicationTheme  {
                AppNavHost()
            }
        }
    }
}
