package com.tuempresa.citasmedicas.util

sealed class NetworkResult<out T> {
    data class Loading<T>(val data: T? = null) : NetworkResult<T>()
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error<T>(val message: String, val data: T? = null) : NetworkResult<T>()
}
