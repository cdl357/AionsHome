package com.aion.chat;

public final class CameraPreviewSessionGate {
    private long generation;
    private boolean active;

    synchronized long start() {
        active = true;
        return ++generation;
    }

    synchronized void stop() {
        active = false;
        generation++;
    }

    synchronized boolean isCurrent(long token) {
        return active && token == generation;
    }
}
