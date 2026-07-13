package dev.nucleusframework.rodio

import dev.nucleusframework.rodio.internal.NativePlaybackCallbackRaw
import dev.nucleusframework.rodio.internal.NativeRodioBridge
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the GraalVM `reachability-metadata.json` against drift from the actual
 * JNI contract. Every native downcall on [NativeRodioBridge], every upcall on
 * [NativePlaybackCallbackRaw] (invoked from Rust via `call_method`) and the
 * [RodioException] constructor (created via `throw_new`) must stay registered
 * with matching signatures, otherwise a native-image build would break at
 * runtime with an unsatisfied-link or class-not-found failure.
 */
class RodioReachabilityMetadataTest {
    @Test
    fun nativeBridgeDowncallsMatchReachabilityMetadata() {
        val entry = metadata.getValue(NativeRodioBridge::class.java.name)
        assertTrue(entry.jniAccessible, "NativeRodioBridge must be jniAccessible")

        val expected =
            NativeRodioBridge::class.java.declaredMethods
                .filter { Modifier.isNative(it.modifiers) }
                .associate { it.name to it.jniParameterTypes() }

        assertTrue(expected.isNotEmpty(), "expected at least one native method on the bridge")
        for ((name, params) in expected) {
            assertEquals(
                expected = params,
                actual = entry.methodParameterTypes(name),
                message = "reachability metadata for native downcall '$name' drifted from NativeRodioBridge",
            )
        }
    }

    @Test
    fun callbackUpcallsMatchReachabilityMetadata() {
        val entry = metadata.getValue(NativePlaybackCallbackRaw::class.java.name)
        assertTrue(entry.jniAccessible, "NativePlaybackCallbackRaw must be jniAccessible")

        val expected =
            NativePlaybackCallbackRaw::class.java.declaredMethods
                .associate { it.name to it.jniParameterTypes() }

        for ((name, params) in expected) {
            assertEquals(
                expected = params,
                actual = entry.methodParameterTypes(name),
                message = "reachability metadata for upcall '$name' drifted from NativePlaybackCallbackRaw",
            )
        }
    }

    @Test
    fun exceptionConstructorIsRegisteredForThrowNew() {
        val entry = metadata.getValue(RodioException::class.java.name)
        assertTrue(entry.jniAccessible, "RodioException must be jniAccessible for throw_new")
        assertEquals(
            expected = listOf("java.lang.String"),
            actual = entry.methodParameterTypes("<init>"),
            message = "RodioException(String) must stay registered for the native throw path",
        )
    }

    private data class ReflectedType(
        val jniAccessible: Boolean,
        val methods: Map<String, List<String>>,
    ) {
        fun methodParameterTypes(name: String): List<String> =
            methods[name] ?: error("method '$name' missing from reachability metadata")
    }

    private companion object {
        const val METADATA_PATH =
            "src/main/resources/META-INF/native-image/dev.nucleusframework/nucleus.rodio/reachability-metadata.json"

        val TYPE_ENTRY_REGEX =
            Regex(
                """"type"\s*:\s*"(?<type>[^"]+)"(?<body>.*?)(?=\{\s*"type"|]\s*}\s*$)""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
        val JNI_ACCESSIBLE_REGEX = Regex(""""jniAccessible"\s*:\s*(true|false)""")
        val METHOD_REGEX =
            Regex(
                """"name"\s*:\s*"(?<name>[^"]+)"\s*,\s*"parameterTypes"\s*:\s*\[(?<params>[^]]*)]""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
        val STRING_LITERAL_REGEX = Regex(""""([^"]+)"""")

        val metadata: Map<String, ReflectedType> by lazy {
            val json = Files.readString(Path.of(METADATA_PATH))
            TYPE_ENTRY_REGEX
                .findAll(json)
                .associate { match ->
                    val body = match.groups["body"]!!.value
                    match.groups["type"]!!.value to
                        ReflectedType(
                            jniAccessible = JNI_ACCESSIBLE_REGEX.find(body)?.groupValues?.get(1).toBoolean(),
                            methods =
                                METHOD_REGEX
                                    .findAll(body)
                                    .associate { m ->
                                        m.groups["name"]!!.value to
                                            STRING_LITERAL_REGEX
                                                .findAll(m.groups["params"]!!.value)
                                                .map { it.groupValues[1] }
                                                .toList()
                                    },
                        )
                }
        }

        fun java.lang.reflect.Method.jniParameterTypes(): List<String> =
            parameterTypes.map { it.toJniMetadataTypeName() }

        fun Class<*>.toJniMetadataTypeName(): String =
            when (this) {
                java.lang.Long.TYPE -> "long"
                Integer.TYPE -> "int"
                java.lang.Boolean.TYPE -> "boolean"
                java.lang.Float.TYPE -> "float"
                else -> name
            }
    }
}
