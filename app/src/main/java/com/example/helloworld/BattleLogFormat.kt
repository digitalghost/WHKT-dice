package com.example.helloworld

/** Pure parsing of battle log messages: category tags and keyword highlight ranges. */
object BattleLogFormat {
    enum class Kind(val label: String) {
        SHOOT("射击"), MELEE("近战"), DICE("掷骰"), INJURY("残废"), SCORE("计分"), CP("CP"),
        ORDER("特工"), TURN("回合"), NOTE("备注"), UNDO("撤回"), OTHER("记录")
    }

    /** Filter groups shown above the journal. */
    enum class Filter(val label: String) {
        ALL("全部"), COMBAT("战斗"), SCORE("计分 · CP"), OPERATIVE("特工"), NOTE("备注");

        fun accepts(kind: Kind): Boolean = when (this) {
            ALL -> true
            COMBAT -> kind == Kind.SHOOT || kind == Kind.MELEE || kind == Kind.INJURY || kind == Kind.DICE
            SCORE -> kind == Kind.SCORE || kind == Kind.CP
            OPERATIVE -> kind == Kind.ORDER
            NOTE -> kind == Kind.NOTE
        }
    }

    enum class Role { SIDE_A, SIDE_B, WEAPON, SCORE, CP, DANGER }

    data class Span(val start: Int, val end: Int, val role: Role)

    fun kind(message: String, manual: Boolean): Kind {
        val head = message.substringBefore("\n")
        return when {
            head.startsWith("↶") -> Kind.UNDO
            head.startsWith("射击") -> Kind.SHOOT
            head.startsWith("近战") -> Kind.MELEE
            head.startsWith("掷骰") -> Kind.DICE
            head.contains("残废") -> Kind.INJURY
            head.contains("VP") || head.contains("击杀等级") -> Kind.SCORE
            head.contains("CP") -> Kind.CP
            head.startsWith("第 ") || head.startsWith("进入第") || head.contains("先手") -> Kind.TURN
            head.contains("激活") || head.contains("命令") || head.contains("标记") || head.contains("AP）") ||
                head.contains("使用") || head.contains("选择") -> Kind.ORDER
            manual -> Kind.NOTE
            else -> Kind.OTHER
        }
    }

    /** Headline and optional process steps ("过程：a；b；c"). */
    fun split(message: String): Pair<String, List<String>> {
        val head = message.substringBefore("\n")
        val rest = message.substringAfter("\n", "").trim()
        if (rest.isEmpty()) return head to emptyList()
        val steps = rest.removePrefix("过程：").split("；", "\n").map(String::trim).filter(String::isNotEmpty)
        return head to steps
    }

    /** Builds the term table for one battle; longer terms win on overlap. */
    fun lexicon(battle: BattleRecord): Map<String, Role> {
        val terms = linkedMapOf<String, Role>()
        listOf(battle.sideA, battle.sideB).forEach { side ->
            side.operatives.forEach { op -> op.weapons.forEach { terms.putIfAbsent(it.name, Role.WEAPON) } }
        }
        listOf(battle.sideA to Role.SIDE_A, battle.sideB to Role.SIDE_B).forEach { (side, role) ->
            terms[side.playerName] = role
            terms[side.rosterName] = role
            side.operatives.forEach { terms[it.displayName] = role }
        }
        return terms.filterKeys { it.isNotBlank() }
    }

    private val score = Regex("""-?\d+\s?VP""")
    private val cp = Regex("""-?\d+\s?CP|CP\s?\d+(\s?→\s?\d+)?""")
    private val danger = Regex("""残废|伤害\s?\d+|已撤回""")

    fun spans(text: String, lexicon: Map<String, Role>): List<Span> {
        val found = mutableListOf<Span>()
        fun free(s: Int, e: Int) = found.none { s < it.end && e > it.start }
        lexicon.keys.sortedByDescending { it.length }.forEach { term ->
            var from = 0
            while (true) {
                val at = text.indexOf(term, from)
                if (at < 0) break
                if (free(at, at + term.length)) found += Span(at, at + term.length, lexicon.getValue(term))
                from = at + term.length
            }
        }
        listOf(score to Role.SCORE, cp to Role.CP, danger to Role.DANGER).forEach { (regex, role) ->
            regex.findAll(text).forEach { if (free(it.range.first, it.range.last + 1)) found += Span(it.range.first, it.range.last + 1, role) }
        }
        return found.sortedBy { it.start }
    }
}
