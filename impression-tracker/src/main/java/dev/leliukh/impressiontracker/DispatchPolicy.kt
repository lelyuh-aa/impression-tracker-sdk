package dev.leliukh.impressiontracker

/** When the SDK sends intercepted impressions to the backend. */
public enum class DispatchPolicy {

    /**
     * Sends each impression as soon as it has been stored, and retries whatever
     * is left over on the next impression or on the daily job.
     */
    IMMEDIATE,

    /**
     * Keeps impressions on the device and sends them in one batch once a day.
     * Costs less battery and radio time on devices that see many impressions.
     */
    DAILY_BATCH,
}
