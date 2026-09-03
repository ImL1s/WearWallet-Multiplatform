package io.github.iml1s.caip.model

/**
 * A generic result type for CAIP operations.
 *
 * This sealed class provides a type-safe way to represent the outcome
 * of operations that can either succeed with a value, fail with an exception,
 * or be in a loading state.
 *
 * @param T The type of the successful result value
 */
sealed class CAIPResult<out T> {
    /**
     * Represents a successful result containing a value.
     *
     * @param data The successful result value
     */
    data class Success<T>(val data: T) : CAIPResult<T>()

    /**
     * Represents a failed result containing an exception.
     *
     * @param exception The exception that caused the failure
     */
    data class Failure(val exception: Exception) : CAIPResult<Nothing>()

    /**
     * Represents a loading state for asynchronous operations.
     */
    class Loading<T> : CAIPResult<T>()

    /**
     * Transform the success value using the provided function.
     *
     * @param transform The transformation function
     * @return A new Result with the transformed value
     */
    inline fun <R> map(transform: (T) -> R): CAIPResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
        is Loading -> Loading()
    }

    /**
     * Transform the success value using a function that returns a Result.
     *
     * @param transform The transformation function returning a Result
     * @return The result of the transformation
     */
    inline fun <R> flatMap(transform: (T) -> CAIPResult<R>): CAIPResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
        is Loading -> Loading()
    }

    /**
     * Get the success value or null if the result is not successful.
     *
     * @return The success value or null
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
        is Loading -> null
    }

    /**
     * Get the success value or throw the exception if failed.
     *
     * @return The success value
     * @throws Exception if the result is a failure
     * @throws IllegalStateException if the result is still loading
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw exception
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    /**
     * Check if the result is successful.
     *
     * @return true if successful, false otherwise
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Check if the result is a failure.
     *
     * @return true if failed, false otherwise
     */
    fun isFailure(): Boolean = this is Failure

    /**
     * Check if the result is loading.
     *
     * @return true if loading, false otherwise
     */
    fun isLoading(): Boolean = this is Loading

    /**
     * Execute an action if the result is successful.
     *
     * @param action The action to execute with the success value
     * @return This result for chaining
     */
    inline fun onSuccess(action: (T) -> Unit): CAIPResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Execute an action if the result is a failure.
     *
     * @param action The action to execute with the exception
     * @return This result for chaining
     */
    inline fun onFailure(action: (Exception) -> Unit): CAIPResult<T> {
        if (this is Failure) action(exception)
        return this
    }

    companion object {
        /**
         * Create a successful result.
         *
         * @param value The success value
         * @return A successful Result
         */
        fun <T> success(value: T): CAIPResult<T> = Success(value)

        /**
         * Create a failed result.
         *
         * @param exception The failure exception
         * @return A failed Result
         */
        fun <T> failure(exception: Exception): CAIPResult<T> = Failure(exception)

        /**
         * Create a loading result.
         *
         * @return A loading Result
         */
        fun <T> loading(): CAIPResult<T> = Loading()

        /**
         * Execute a block and wrap the result.
         *
         * @param block The block to execute
         * @return Success with the result or Failure with the caught exception
         */
        inline fun <T> runCatching(block: () -> T): CAIPResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Failure(e)
            }
        }
    }
}

/**
 * Get the success value or execute the fallback function.
 *
 * @param onFailure The function to execute if the result is a failure
 * @return The success value or the fallback value
 */
inline fun <T> CAIPResult<T>.getOrElse(onFailure: (Exception) -> T): T = when (this) {
    is CAIPResult.Success -> data
    is CAIPResult.Failure -> onFailure(exception)
    is CAIPResult.Loading -> throw IllegalStateException("Cannot get value from Loading state")
}

/**
 * Get the success value or return the default value.
 *
 * @param defaultValue The default value to return if not successful
 * @return The success value or the default value
 */
fun <T> CAIPResult<T>.getOrDefault(defaultValue: T): T = when (this) {
    is CAIPResult.Success -> data
    is CAIPResult.Failure -> defaultValue
    is CAIPResult.Loading -> defaultValue
}

/**
 * Recover from a failure by transforming the exception into a success value.
 *
 * @param transform The transformation function for the exception
 * @return Success with either the original value or the recovered value
 */
inline fun <T> CAIPResult<T>.recover(transform: (Exception) -> T): CAIPResult<T> = when (this) {
    is CAIPResult.Success -> this
    is CAIPResult.Failure -> CAIPResult.Success(transform(exception))
    is CAIPResult.Loading -> this
}

/**
 * Recover from a failure by transforming the exception into another Result.
 *
 * @param transform The transformation function for the exception
 * @return The original success or the recovered result
 */
inline fun <T> CAIPResult<T>.recoverCatching(transform: (Exception) -> CAIPResult<T>): CAIPResult<T> = when (this) {
    is CAIPResult.Success -> this
    is CAIPResult.Failure -> transform(exception)
    is CAIPResult.Loading -> this
}
