package com.aion.chat.supervision;

public final class LatestRequestGate {
    private long generation;

    synchronized long next() {
        return ++generation;
    }

    synchronized boolean isCurrent(long candidate) {
        return generation == candidate;
    }

    synchronized void cancel() {
        generation++;
    }
}
