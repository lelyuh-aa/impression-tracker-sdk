package dev.leliukh.impressiontracker

/**
 * Public entry point of the impression tracking SDK.
 *
 * Calling anything here is optional: the SDK starts itself when the host
 * application's process starts, and falls back to [ImpressionTrackerConfig.DEFAULT]
 * overridden by any `meta-data` found in the host's manifest.
 *
 * Settings can be declared in the host manifest instead of in code:
 * ```xml
 * <meta-data android:name="dev.leliukh.impressiontracker.ENDPOINT"
 *            android:value="https://example.com/impressions" />
 * <meta-data android:name="dev.leliukh.impressiontracker.DISPATCH_POLICY"
 *            android:value="DAILY_BATCH" />
 * ```
 */
public object ImpressionTracker {

    /** Settings currently in force. */
    @Volatile
    internal var config: ImpressionTrackerConfig = ImpressionTrackerConfig.DEFAULT
        private set

    /**
     * Replaces the settings the SDK runs with.
     *
     * Takes effect on the next impression; impressions already stored are sent
     * to the new endpoint.
     *
     * @param config Settings to use from now on.
     */
    public fun configure(config: ImpressionTrackerConfig) {
        this.config = config
    }
}
