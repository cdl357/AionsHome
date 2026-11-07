package com.aion.chat;

/**
 * Keeps repeated launcher intents from rebuilding the existing WebView task.
 */
public final class LauncherResumePolicy {

    private LauncherResumePolicy() {
    }

    static boolean shouldFinishWithoutLaunching(
            boolean isTaskRoot,
            boolean forceAddressPicker) {
        return !isTaskRoot && !forceAddressPicker;
    }
}
