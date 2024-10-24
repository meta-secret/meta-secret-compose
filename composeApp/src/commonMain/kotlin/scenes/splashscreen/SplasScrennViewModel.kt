package scenes.splashscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import repo.TaskRepository

class SplashScreenViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {
    private val _navigationEvent = MutableStateFlow<SplashNavigationEvent>(SplashNavigationEvent.Idle)
    val navigationEvent: StateFlow<SplashNavigationEvent> = _navigationEvent

    fun onAppear() {
        viewModelScope.launch {
            loadData()
            delay(3000)

            when {
                isOnboardingComplete() -> {
                    if (checkAuth()) {
                        _navigationEvent.value = SplashNavigationEvent.NavigateToMain
                    } else {
                        _navigationEvent.value = SplashNavigationEvent.NavigateToSignUp
                    }
                }
                else -> {
                    _navigationEvent.value = SplashNavigationEvent.NavigateToOnboarding
                }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            taskRepository.loadTodos().collect { todoList ->
                println(todoList)
            }
        }
    }

    fun isOnboardingComplete(): Boolean {
        return false //temporary
    }

    fun checkAuth(): Boolean {
        return false
    }
}

enum class SplashNavigationEvent {
    Idle,
    NavigateToMain,
    NavigateToSignUp,
    NavigateToOnboarding
}