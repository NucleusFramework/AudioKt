package dev.nucleusframework.rodio

/**
 * Raised by the native rodio backend. Thrown from JNI
 * (`dev.nucleusframework.rodio.RodioException`) when a native call fails.
 */
class RodioException(message: String) : RuntimeException(message)