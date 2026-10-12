package net.enthusia.staff.discordbot;

/**
 * Aggregate process-lifetime counters, never receipt identities, text, paths,
 * credentials, or individual failure details. Reset on JVM restart.
 */
record AiMirrorRecoveryMetrics(
        long eligiblePostSend, long persisted, long ingressCapacityDrops,
        long ingressStorageFailures, long ingressCancelled,
        long fastConfirmed, long fastTransientRetained, long fastDeferred,
        long fastTerminal, long fastStorageFailures,
        long slowConfirmed, long slowTransient, long slowDeferred,
        long slowTerminal, long slowExhausted, long slowStorageFailures,
        long expiredReceipts) {

    static AiMirrorRecoveryMetrics capture(AiMirrorDurableRecoverySession session) {
        var ingress = session.ingressStats();
        var fast = session.immediateStats();
        var slow = session.recoveryStats();
        return new AiMirrorRecoveryMetrics(
                ingress.eligiblePostSend(), ingress.persisted(),
                ingress.capacityDrops(), ingress.storageFailures(),
                ingress.cancelledPending(), fast.confirmed(),
                fast.transientRetained(), fast.deferredToRecovery(),
                fast.terminalDiscarded(), fast.storageFailures(),
                slow.confirmed(), slow.transientFailures(),
                slow.deferred(), slow.terminal(), slow.exhausted(),
                slow.storageFailures(), slow.expiredReceipts());
    }

    String json() {
        StringBuilder body = new StringBuilder(460);
        body.append("{");
        number(body, "eligiblePostSend", eligiblePostSend);
        number(body, "persisted", persisted);
        number(body, "ingressCapacityDrops", ingressCapacityDrops);
        number(body, "ingressStorageFailures", ingressStorageFailures);
        number(body, "ingressCancelled", ingressCancelled);
        number(body, "fastConfirmed", fastConfirmed);
        number(body, "fastTransientRetained", fastTransientRetained);
        number(body, "fastDeferred", fastDeferred);
        number(body, "fastTerminal", fastTerminal);
        number(body, "fastStorageFailures", fastStorageFailures);
        number(body, "slowConfirmed", slowConfirmed);
        number(body, "slowTransient", slowTransient);
        number(body, "slowDeferred", slowDeferred);
        number(body, "slowTerminal", slowTerminal);
        number(body, "slowExhausted", slowExhausted);
        number(body, "slowStorageFailures", slowStorageFailures);
        number(body, "expiredReceipts", expiredReceipts);
        body.setLength(body.length() - 1);
        return body.append("}").toString();
    }

    private static void number(StringBuilder body, String key, long value) {
        body.append('"').append(key).append("\":").append(value).append(',');
    }
}
