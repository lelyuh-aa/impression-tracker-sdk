# Keep rules applied automatically to any application that uses this SDK.
#
# The SDK reaches both ad networks through reflection and never references their
# types, so R8 has no way of knowing that these classes and members are in use.
# Without the rules below it renames them and the hooks stop finding anything -
# quietly, since the SDK is built to degrade instead of crash.
#
# Only what is actually looked up by name is kept. The rest of both libraries
# stays available for shrinking and obfuscation as usual.

# ---------------------------------------------------------------- AlphaAds

# This network keeps a single impression listener in a private field and offers
# no way to add a second one, so the SDK replaces that field with a decorating
# Proxy. That makes the whole library off limits to R8, and narrow rules are not
# enough: R8 merges the host's listener into a synthetic class and then narrows
# the field to that exact class, after which no Proxy can be stored in it.
#
# Keeping the library whole costs a few kilobytes and is the price of reaching
# into internals that were never meant to be reached. See the README for the
# proper fix, which is an add-listener method in the network's public API.
-keep class com.alphaads.sdk.** { *; }

# ---------------------------------------------------------- BetaAds

# Reached by name: the INSTANCE field of the Kotlin object, and the method that
# registers one more listener.
-keep class com.betaads.sdk.BetaAds { *; }

# Implemented by a dynamic Proxy; the callback name is compared at dispatch time.
-keep interface com.betaads.sdk.BetaImpressionListener { *; }

# Read through these getters, like the other network's payload.
-keepclassmembers class com.betaads.sdk.BetaImpressionInfo {
    java.lang.String getCreativeId();
    java.lang.String getNetworkName();
}
