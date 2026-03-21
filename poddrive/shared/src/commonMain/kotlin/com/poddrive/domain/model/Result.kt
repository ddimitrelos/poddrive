package com.poddrive.domain.model

/**
 * Two-parameter typed Result for KMP use cases.
 * Replaces kotlin.Result<T> so errors are typed as AppError rather than Throwable.
 * All KMP use case return types: Result<T, AppError>.
 * [Source: architecture.md#API & Communication Patterns, AR5]
 */
sealed class Result<out T, out E> {
    data class Success<out T>(val value: T) : Result<T, Nothing>()
    data class Failure<out E>(val error: E) : Result<Nothing, E>()
}

// ---------------------------------------------------------------------------
// Convenience extensions used by use cases and platform ViewModels
// ---------------------------------------------------------------------------

inline fun <T, E> Result<T, E>.onSuccess(block: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) block(value)
    return this
}

inline fun <T, E> Result<T, E>.onFailure(block: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) block(error)
    return this
}

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Success -> Result.Success(transform(value))
    is Result.Failure -> this
}

fun <T, E> Result<T, E>.getOrNull(): T? = if (this is Result.Success) value else null

fun <T, E> Result<T, E>.errorOrNull(): E? = if (this is Result.Failure) error else null
