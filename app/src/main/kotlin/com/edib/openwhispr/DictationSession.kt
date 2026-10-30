package com.edib.openwhispr

/** Generation tokens prevent cancelled work from delivering results to a later session. */
class DictationSession {
    private var generation = 0L
    private var active = false
    @Synchronized fun begin(): Long { active = true; return ++generation }
    @Synchronized fun cancel() { active = false; generation++ }
    @Synchronized fun isCurrent(token: Long): Boolean = active && token == generation
    @Synchronized fun runIfCurrent(token: Long, action: () -> Unit) {
        if (active && token == generation) action()
    }
}
