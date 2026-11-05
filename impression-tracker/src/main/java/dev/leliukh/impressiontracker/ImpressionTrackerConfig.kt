package dev.leliukh.impressiontracker

/**
 * Settings of the impression tracking SDK.
 *
 * @property endpoint Absolute URL that receives the impressions.
 * @property dispatchPolicy When impressions are sent.
 */
public data class ImpressionTrackerConfig(
    public val endpoint: String,
    public val dispatchPolicy: DispatchPolicy,
) {

    public companion object {

        /**
         * Settings used when the host application configures nothing.
         *
         * The endpoint points at port 8080 of the machine hosting an Android
         * emulator, which is what `10.0.2.2` means inside one.
         */
        public val DEFAULT: ImpressionTrackerConfig = ImpressionTrackerConfig(
            endpoint = "http://10.0.2.2:8080/impressions",
            dispatchPolicy = DispatchPolicy.IMMEDIATE,
        )
    }
}
