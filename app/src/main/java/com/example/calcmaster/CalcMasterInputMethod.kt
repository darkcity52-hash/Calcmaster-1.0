package com.example.calcmaster

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.calcmaster.ui.theme.CalcMasterTheme

class CalcMasterInputMethod : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registryLifecycle by lazy { LifecycleRegistry(this) }
    private val storeViewModel by lazy { ViewModelStore() }
    private val controllerSavedState by lazy { SavedStateRegistryController.create(this) }

    override val lifecycle: Lifecycle 
        get() = registryLifecycle

    override val viewModelStore: ViewModelStore 
        get() = storeViewModel

    override val savedStateRegistry: SavedStateRegistry 
        get() = controllerSavedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        controllerSavedState.performRestore(null)
        registryLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CalcMasterInputMethod)
            setViewTreeViewModelStoreOwner(this@CalcMasterInputMethod)
            setViewTreeSavedStateRegistryOwner(this@CalcMasterInputMethod)
            
            setContent {
                CalcMasterTheme {
                    HeliosSystemKeyboard(
                        onInputText = { text -> 
                            currentInputConnection?.commitText(text, 1) 
                        },
                        onBackspace = { 
                            currentInputConnection?.deleteSurroundingText(1, 0) 
                        },
                        onPanic = { 
                            requestHideSelf(0) 
                        }
                    )
                }
            }
        }.also {
            // 🔥 Fuerza a la interfaz de Compose a inicializar su tamaño y dibujarse
            registryLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registryLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onDestroy() {
        // 🛑 Cierra los estados del ciclo de vida en orden inverso
        registryLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registryLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registryLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        storeViewModel.clear()
        super.onDestroy()
    }
}
