package metasecret.project.com

// TEST-only literal profile data. Numeric gaps and arbitrary modes are refused.
internal class TestRecoveryProfile private constructor(
    val number: Int, val mode: String, private val cycles: IntRange,
) {
    val tag get() = "test$number:$mode"
    val future get() = number in futureNumbers

    companion object {
        private val futureNumbers = setOf(26,27,28,29,30,31,32,33,34,35,36,46,47,48,49)
        private val catalog = listOf(
            TestRecoveryProfile(24,"ios-multi-secret-lifecycle",13..39),
            TestRecoveryProfile(25,"web-secondary-creator-lifecycle",13..39),
            TestRecoveryProfile(26,"android-secondary-creator-lifecycle",13..39),
            TestRecoveryProfile(27,"ios-secondary-creator-lifecycle",13..39),
            TestRecoveryProfile(28,"web-secondary-creator-lifecycle",1..27),
            TestRecoveryProfile(29,"android-distributed-creator-lifecycle",1..27),
            TestRecoveryProfile(30,"ios-distributed-creator-lifecycle",1..27),
            TestRecoveryProfile(31,"web-secondary-creator-lifecycle",1..27),
            TestRecoveryProfile(32,"android-owner-creator-lifecycle",1..27),
            TestRecoveryProfile(33,"ios-two-secret-owner-lifecycle",1..27),
            TestRecoveryProfile(34,"web-secondary-creator-lifecycle",1..18),
            TestRecoveryProfile(35,"android-secondary-creator-lifecycle",1..27),
            TestRecoveryProfile(36,"ios-distributed-creator-lifecycle",1..27),
            TestRecoveryProfile(46,"web-distributed-creator-removal-rejoin-recovery-lifecycle",7..33),
            TestRecoveryProfile(47,"web-distributed-creator-declined-removal-recovery-lifecycle",1..27),
            TestRecoveryProfile(48,"android-distributed-creator-declined-removal-recovery-lifecycle",1..27),
            TestRecoveryProfile(49,"ios-distributed-creator-declined-removal-recovery-lifecycle",1..27),
        )
        fun selected(tag: String?) = catalog.singleOrNull { it.tag == tag }
        fun readFuture(raw: String): Pair<List<String>, String>? {
            if (raw.length > 2048 || raw.any { it.code !in 0..127 }) return null
            for (profile in catalog.filter { it.future }) {
                val pattern = Regex("""\A\{"version":1,"runId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","testNumber":${profile.number},"attempt":([1-9][0-9]{0,8}),"blockId":"([A-Za-z0-9][A-Za-z0-9_.-]{0,79})","cycle":([1-9][0-9]{0,2}),"logicalStep":1,"mode":"${profile.mode}","contextId":"([0-9a-f]{32})","invocationId":"([0-9a-f]{32})","platform":"(android|ios|web)","role":"(sender|receiver)"\}\z""")
                val fields = pattern.matchEntire(raw)?.groupValues?.drop(1) ?: continue
                if (fields[3].toInt() in profile.cycles) return fields to profile.tag
            }
            return null
        }
    }
}
