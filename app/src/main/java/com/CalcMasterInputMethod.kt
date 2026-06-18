package com.example.calcmaster

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.calcmaster.ui.theme.CalcMasterTheme

class CalcMasterInputMethod : InputMethodService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val viewModelStore: ViewModelStore
        get() = viewModelStore

    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
    }

    override fun onCreateInputView(): View {
        // Inicializamos el contenedor Compose dentro del entorno del servicio de Android
        return ComposeView(this).apply {
            setViewTreeViewModelStoreOwner(this@CalcMasterInputMethod)
            setViewTreeSavedStateRegistryOwner(this@CalcMasterInputMethod)
            
            setContent {
                CalcMasterTheme {
                    // Pasamos la conexión de texto actual para interactuar con otras apps
                    HeliosSystemKeyboard(
                        onInputText = { text ->
                            currentInputConnection?.commitText(text, 1)
                        },
                        onBackspace = {
                            currentInputConnection?.deleteSurroundingText(1, 0)
                        },
                        onPanic = {
                            // Cierra el teclado inmediatamente si se activa el pánico
                            requestHideSelf(0)
                        }
                    )
                }
            }
        }
    }
}
