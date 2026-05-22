package com.example.quickbite.android.screens

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MenuEntry(
    val name       : String,
    val category   : String,
    val price      : Double = 0.0,
    val description: String = ""
)

sealed class MenuUiState {
    object Loading                       : MenuUiState()
    data class Success(val items: List<MenuEntry>) : MenuUiState()
    object Empty                         : MenuUiState()
    data class Error(val message: String): MenuUiState()
}

class ClientViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _currentRestaurantId = MutableStateFlow<String?>(null)
    val currentRestaurantId: StateFlow<String?> = _currentRestaurantId.asStateFlow()

    private val _menuState = MutableStateFlow<MenuUiState>(MenuUiState.Loading)
    val menuState: StateFlow<MenuUiState> = _menuState.asStateFlow()

    private var menuListener: ListenerRegistration? = null

    fun loadMenu(restaurantId: String) {
        if (_currentRestaurantId.value == restaurantId) return
        _currentRestaurantId.value = restaurantId
        _menuState.value = MenuUiState.Loading
        menuListener?.remove()
        menuListener = db.collection("restaurants").document(restaurantId)
            .collection("menu")
            .addSnapshotListener { snap, error ->
                _menuState.value = when {
                    error != null -> MenuUiState.Error(
                        when {
                            error.message?.contains("PERMISSION_DENIED") == true ->
                                "Acces refuzat. Verifică ID-ul restaurantului."
                            error.message?.contains("NOT_FOUND") == true ->
                                "Restaurantul nu a fost găsit."
                            else ->
                                "Eroare la încărcarea meniului."
                        }
                    )
                    snap == null || snap.isEmpty -> MenuUiState.Empty
                    else -> {
                        val items = snap.documents.mapNotNull { doc ->
                            MenuEntry(
                                name        = doc.getString("name") ?: return@mapNotNull null,
                                category    = doc.getString("category") ?: "",
                                price       = doc.getDouble("price") ?: 0.0,
                                description = doc.getString("description") ?: ""
                            )
                        }
                        if (items.isEmpty()) MenuUiState.Empty else MenuUiState.Success(items)
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        menuListener?.remove()
    }
}
