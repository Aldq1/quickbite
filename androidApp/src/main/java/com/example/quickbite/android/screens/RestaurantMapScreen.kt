package com.example.quickbite.android.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private val MapBrand = Color(0xFFE8430A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantMapScreen(onBack: () -> Unit) {
    val quickbiteHq = LatLng(44.4323, 26.1063)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(quickbiteHq, 15f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Locație QuickBite",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.Rounded.ArrowBack,
                            contentDescription = "Înapoi"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor       = MapBrand,
                    titleContentColor    = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor     = Color.White
                )
            )
        }
    ) { innerPadding ->
        GoogleMap(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            cameraPositionState = cameraPositionState,
            properties          = MapProperties(mapType = MapType.NORMAL),
            uiSettings          = MapUiSettings(
                zoomControlsEnabled    = true,
                myLocationButtonEnabled = false,
                mapToolbarEnabled       = true
            )
        ) {
            Marker(
                state   = MarkerState(position = quickbiteHq),
                title   = "QuickBite Central",
                snippet = "Restaurantul nostru principal — Bine ați venit!"
            )
        }
    }
}
