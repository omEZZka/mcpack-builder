package com.mcpackbuilder.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.domain.model.SavedBuild
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    val builds: StateFlow<List<SavedBuild>> = container.library.builds

    init {
        container.library.refresh()
    }

    fun delete(id: String) = container.library.delete(id)

    fun refresh() = container.library.refresh()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container) }
        }
    }
}
