package com.baraa.training.ecommerce.ui.auth.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.baraa.training.ecommerce.data.datasource.datastore.AppPreferencesDataSource
import com.baraa.training.ecommerce.data.models.Resource
import com.baraa.training.ecommerce.data.models.user.UserDetailsModel
import com.baraa.training.ecommerce.data.repository.auth.FirebaseAuthRepository
import com.baraa.training.ecommerce.data.repository.auth.FirebaseAuthRepositoryImpl
import com.baraa.training.ecommerce.data.repository.common.AppDataStoreRepositoryImpl
import com.baraa.training.ecommerce.data.repository.common.AppPreferenceRepository
import com.baraa.training.ecommerce.data.repository.user.UserPreferenceRepository
import com.baraa.training.ecommerce.domain.models.toUserDetailsPreferences
import com.baraa.training.ecommerce.utils.isValidEmail
import com.baraa.training.ecommerce.data.repository.user.UserPreferenceRepositoryImpl
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginViewModel(
    private val appPreferenceRepository: AppPreferenceRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    private val _loginState = MutableSharedFlow<Resource<UserDetailsModel>>()
    val loginState: SharedFlow<Resource<UserDetailsModel>> = _loginState.asSharedFlow()

    val email = MutableStateFlow("")
    val password = MutableStateFlow("")

    /*
    * ال combine عشان أعمل collect لل email و ال password بنفس ال bloke
    * ومسموحلي أستخدم بس لحد 5 flows بداخل ال combine وإذا كان عندي أكثر
    * بقسمهم على قسمين (Two combines)
    */
    private val isLoginIsValid: Flow<Boolean> = combine(email, password) { email, password ->
        email.isValidEmail() && password.length >= 6
    }

    fun loginWithEmailAndPassword() = viewModelScope.launch(IO) {
        val email = email.value
        val password = password.value
        if (isLoginIsValid.first()) {
            handleLoginFlow { authRepository.loginWithEmailAndPassword(email, password) }
        } else {
            _loginState.emit(Resource.Error(Exception("Invalid email or password")))
        }
    }

    fun loginWithGoogle(idToken: String) {
        handleLoginFlow { authRepository.loginWithGoogle(idToken) }
    }

    fun loginWithFacebook(token: String) {
        handleLoginFlow { authRepository.loginWithFacebook(token) }
    }

    private fun handleLoginFlow(loginFlow: suspend () -> Flow<Resource<UserDetailsModel>>) =
        viewModelScope.launch(IO) {
            loginFlow().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        savePreferenceData(resource.data!!)
                        _loginState.emit(Resource.Success(resource.data))
                    }

                    else -> _loginState.emit(resource)
                }
            }
        }

    private suspend fun savePreferenceData(userDetailsModel: UserDetailsModel) {
        appPreferenceRepository.saveLoginState(true)
        userPreferenceRepository.updateUserDetails(userDetailsModel.toUserDetailsPreferences())
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}

// create viewmodel factory class
class LoginViewModelFactory(
    contextValue: Context
) : ViewModelProvider.Factory {

    private val appPreferenceRepository =
        AppDataStoreRepositoryImpl(AppPreferencesDataSource(contextValue))
    private val userPreferenceRepository = UserPreferenceRepositoryImpl(contextValue)
    private val authRepository = FirebaseAuthRepositoryImpl()

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return LoginViewModel(
                appPreferenceRepository,
                userPreferenceRepository,
                authRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}