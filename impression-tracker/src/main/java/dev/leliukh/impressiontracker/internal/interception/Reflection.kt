package dev.leliukh.impressiontracker.internal.interception

/**
 * Reads a getter from an object whose type the SDK deliberately does not
 * reference, so it can only be reached by name.
 *
 * @receiver Object to read from.
 * @param getterName Name of a no argument getter.
 * @return The value, or `null` when the getter is missing, inaccessible or does
 *   not return a `String`.
 */
internal fun Any.readStringGetter(getterName: String): String? =
    try {
        javaClass.getMethod(getterName)
            .apply { isAccessible = true }
            .invoke(this) as? String
    } catch (_: ReflectiveOperationException) {
        null
    }
