package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllTeamsBattleSimulationTest {

    private val zeroWeaponSupportOperatives = setOf(
        "kt_inb_cat_unit",
        "kt_inb_gheistskull",
        "inquisitorial_agents_tome_skull",
        "spectre_squad_12"
    )

    private fun makeBattle(
        name: String,
        rosterA: SavedRoster,
        rosterB: SavedRoster,
        equipmentA: String = "破片手雷、穿甲手雷",
        equipmentB: String = "烟雾手雷、便携掩体"
    ): BattleRecord = BattleRules.create(
        BattleSetup(
            name = name,
            mission = "关键行动 #1",
            killzone = "沃库斯",
            keyOp = "安全传输",
            dropZoneA = "A",
            dropZoneB = "B",
            playerA = "${RosterCatalog.team(rosterA.teamId).name}指挥官",
            playerB = "${RosterCatalog.team(rosterB.teamId).name}指挥官",
            equipmentA = equipmentA,
            equipmentB = equipmentB,
            tacticalA = "削弱敌军",
            tacticalB = "据守阵地",
            rosterA = rosterA,
            rosterB = rosterB,
            primaryA = ScoreType.KILL,
            primaryB = ScoreType.KEY
        )
    )

    private fun updateWounds(battle: BattleRecord, sideId: String, memberId: String, wounds: Int): BattleRecord {
        val side = battle.side(sideId)
        val updatedSide = side.copy(
            operatives = side.operatives.map { o ->
                if (o.memberId == memberId) BattleRules.setWounds(o, wounds) else o
            }
        )
        return BattleRules.syncKills(BattleFlow.side(battle, updatedSide))
    }

    @Test
    fun all48CanonicalRostersMatchOfficialCompositionRules() {
        val teams = RosterCatalog.teams
        assertEquals("Expected all 48 Kill Teams", 48, teams.size)

        teams.forEach { team ->
            val roster = CanonicalRosters.forTeam(team.id)
            assertTrue("Roster for ${team.id} should not be empty", roster.members.isNotEmpty())
            assertTrue("Roster for ${team.id} should have between 5 and 14 operatives", roster.members.size in 5..14)

            roster.members.forEach { member ->
                val template = RosterCatalog.operative(team, member.operativeId)
                assertNotNull("Operative ${member.operativeId} missing in ${team.id}", template)
                if (member.operativeId !in zeroWeaponSupportOperatives) {
                    assertTrue(
                        "Operative ${member.operativeId} in ${team.id} must have selected weapons",
                        member.weaponIds.isNotEmpty()
                    )
                    val matched = template!!.weapons.filter { template.matchesWeaponId(it.id, member.weaponIds) }
                    assertTrue(
                        "Operative ${member.operativeId} in ${team.id} must match at least 1 weapon",
                        matched.isNotEmpty()
                    )
                }
            }
        }
    }

    @Test
    fun grenadierAndExpendableRulesAppliedAcrossAllTeams() {
        val grenadierTeamsAndOps = listOf(
            "CHAOS-PM" to ("pm_bombardier" to 3),
            "IMP-AOD" to ("aod_grenadier" to 3),
            "KT-BLOODED" to ("blooded_02" to 3),
            "KT-BROOD_BROTHERS" to ("kt_bb_sapper" to 3),
            "KT-HEARTHKYN_SALVAGERS" to ("hearthkyn_salvagers_04" to 3),
            "KT-IMPERIAL_NAVY_BREACHERS" to ("kt_inb_grenadier" to 3),
            "KT-PATHFINDERS" to ("kt_pf_grenadier" to 3),
            "KT-SPECTRE_SQUAD" to ("spectre_squad_03" to 3),
            "KT-TEMPESTUS_AQUILONS" to ("tempestus_aquilons_02" to 4),
            "KT-XV26_STEALTH_BATTLESUITS" to ("kt_xv26_liberator" to 3)
        )
        val dummyOpponent = CanonicalRosters.forTeam("IMP-AOD")
        grenadierTeamsAndOps.forEach { (teamId, pair) ->
            val (opId, expectedHit) = pair
            val roster = CanonicalRosters.forTeam(teamId)
            val battle = makeBattle("Grenadier Test $teamId", roster, dummyOpponent)
            val grenadier = battle.sideA.operatives.firstOrNull { it.operativeId == opId }
            assertNotNull("Grenadier $opId should be in canonical roster for $teamId", grenadier)
            val frag = grenadier!!.weapons.firstOrNull { it.name.contains("破片手雷") || it.name.contains("疫病手雷") }
            val krak = grenadier.weapons.firstOrNull { it.name.contains("穿甲手雷") }
            assertNotNull("Grenadier $opId ($teamId) missing Frag/Blight grenade", frag)
            assertNotNull("Grenadier $opId ($teamId) missing Krak grenade", krak)
            assertEquals("Grenadier $opId ($teamId) Frag hit", expectedHit, frag!!.hit)
            assertEquals("Grenadier $opId ($teamId) Krak hit", expectedHit, krak!!.hit)
        }

        val expendableIds = listOf(
            "kt_inb_cat_unit", "kt_inb_gheistskull", "inquisitorial_agents_tome_skull",
            "kt_kom_bomb_squig", "spectre_squad_12", "wrecka_krew_02",
            "kt_xv26_mv15", "kt_xv26_mv75", "nec_can_war",
            "gellerpox_infected_07", "gellerpox_infected_08", "gellerpox_infected_09"
        )
        expendableIds.forEach { opId ->
            assertTrue("Expected $opId to be expendable for Kill Op", BattleRules.isExpendableForKillOp(opId))
        }
    }

    @Test
    fun all48TeamsWeaponsAbilitiesPloysAndEquipmentTestedInCombat() {
        val defenderTemplate = RosterCatalog.team("IMP-AOD").operatives.first()
        val defenderMeleeSnap = BattleRules.snapshot(defenderTemplate.weapons.first { it.kind == WeaponKind.MELEE })

        RosterCatalog.teams.forEach { team ->
            val strategyPloys = TeamPloys.of(team.id, TeamPloys.STRATEGY)
            val firefightPloys = TeamPloys.of(team.id, TeamPloys.FIREFIGHT)
            val factionRules = TeamPloys.of(team.id, TeamPloys.FACTION_RULE)
            val equipment = TeamPloys.of(team.id, TeamPloys.FACTION_EQUIPMENT)
            val archetypes = TeamArchetypes.forTeam(team.id)

            assertEquals("Team ${team.id} should have 4 strategy ploys", 4, strategyPloys.size)
            assertEquals("Team ${team.id} should have 4 firefight ploys", 4, firefightPloys.size)
            assertTrue("Team ${team.id} should have at least 1 faction rule", factionRules.isNotEmpty())
            assertEquals("Team ${team.id} should have 4 faction equipment items", 4, equipment.size)
            assertTrue("Team ${team.id} should have archetypes", archetypes.isNotEmpty())

            strategyPloys.forEach { p ->
                assertFalse("Strategy ploy ${p.name} in ${team.id} has empty body", p.body.isBlank())
                assertFalse("Strategy ploy ${p.name} in ${team.id} has raw markdown header", p.body.contains("### "))
            }
            firefightPloys.forEach { p ->
                assertFalse("Firefight ploy ${p.name} in ${team.id} has empty body", p.body.isBlank())
                assertFalse("Firefight ploy ${p.name} in ${team.id} has raw markdown header", p.body.contains("### "))
            }

            team.operatives.forEach { op ->
                val abilities = AbilityCatalog.forOperative(op.id)
                abilities.forEach { a ->
                    assertTrue("Ability title blank on ${op.id}", a.name.isNotBlank())
                    assertTrue("Ability text blank on ${op.id}:${a.name}", a.description.isNotBlank())
                    if (a.apCost != null) {
                        assertTrue("Invalid AP cost ${a.apCost} on ${op.id}:${a.name}", a.apCost in 0..3)
                    }
                }

                val allWeaponsToTest = op.weapons + CombatWeapons.extraOptions(team.id).map { it.second }
                allWeaponsToTest.forEach { w ->
                    val snap = BattleRules.snapshot(w)
                    assertTrue("Weapon ${w.id} (${w.name}) in ${team.id} has 0 attacks", snap.attacks > 0)
                    assertTrue("Weapon ${w.id} (${w.name}) in ${team.id} has invalid hit ${snap.hit}", snap.hit in 2..6)
                    assertTrue("Weapon ${w.id} (${w.name}) in ${team.id} has negative damage", snap.normalDamage >= 0 && snap.criticalDamage >= 0)

                    // Verify any starred custom weapon rule resolves in TeamDataLoader
                    snap.keywords.filter { it.endsWith("*") }.forEach { kw ->
                        val ruleDef = TeamDataLoader.weaponRule(kw, team.id)
                        assertNotNull("Custom weapon rule '$kw' on ${w.id} in ${team.id} missing from weapon rules", ruleDef)
                    }

                    if (!snap.isMelee) {
                        val sRules = ShootingRuleParser.parse(snap.keywords)
                        val sAttacker = ShootingAttacker(
                            battleSideId = "A",
                            memberId = op.id,
                            playerName = team.name,
                            operativeName = op.name,
                            weaponName = snap.name,
                            maxWounds = op.wounds,
                            currentWounds = op.wounds,
                            attacks = snap.attacks,
                            baseHit = snap.hit,
                            normalDamage = snap.normalDamage,
                            criticalDamage = snap.criticalDamage,
                            rules = sRules
                        )
                        val sDefender = ShootingDefender(
                            battleSideId = "B",
                            memberId = defenderTemplate.id,
                            playerName = "防御方",
                            operativeName = defenderTemplate.name,
                            maxWounds = defenderTemplate.wounds,
                            currentWounds = defenderTemplate.wounds,
                            save = 3
                        )
                        var sState = ShootingCombatRules.start(sAttacker, sDefender)
                        sState = ShootingCombatRules.configure(sState, CoverChoice.NONE, obscured = false, accurateRetained = 0)
                        val rollValues = List(sState.attacker.attacks) { idx -> if (idx == 0) 6 else 4 }
                        sState = ShootingCombatRules.acceptAttackRoll(sState, rollValues)
                        if (sState.phase == ShootingPhase.ATTACK_REROLL) {
                            sState = ShootingCombatRules.finishAttackRerolls(sState)
                        }
                        sState = ShootingCombatRules.finishAttackRetention(sState)
                        if (sState.phase == ShootingPhase.DEFENCE_READY) {
                            val defValues = List(sState.defenceDiceToRoll) { 4 }
                            sState = ShootingCombatRules.acceptDefenceRoll(sState, defValues)
                        }
                        if (sState.phase == ShootingPhase.DEFENCE_REROLL) {
                            sState = ShootingCombatRules.finishDefenceRerolls(sState)
                        }
                        if (sState.phase == ShootingPhase.ALLOCATE) {
                            ShootingCombatRules.recommendedAllocations(sState).forEach { alloc ->
                                sState = ShootingCombatRules.allocate(sState, alloc)
                            }
                            sState = ShootingCombatRules.finishAllocation(sState)
                        }
                        if (sState.phase == ShootingPhase.OVERHEAT) {
                            sState = ShootingCombatRules.skipOverheat(sState)
                        }
                        assertEquals(ShootingPhase.FINISHED, sState.phase)
                        assertTrue("Weapon damage must be non-negative for ${w.id}", sState.weaponDamage >= 0)
                    } else {
                        val mAttacker = MeleeFighter(
                            side = MeleeSide.ATTACKER,
                            battleSideId = "A",
                            memberId = op.id,
                            playerName = team.name,
                            operativeName = op.name,
                            weaponName = snap.name,
                            maxWounds = op.wounds,
                            currentWounds = op.wounds,
                            attacks = snap.attacks,
                            baseHit = snap.hit,
                            normalDamage = snap.normalDamage,
                            criticalDamage = snap.criticalDamage,
                            rules = MeleeRuleParser.parse(snap.keywords)
                        )
                        val mDefender = MeleeFighter(
                            side = MeleeSide.DEFENDER,
                            battleSideId = "B",
                            memberId = defenderTemplate.id,
                            playerName = "防御方",
                            operativeName = defenderTemplate.name,
                            weaponName = defenderMeleeSnap.name,
                            maxWounds = defenderTemplate.wounds,
                            currentWounds = defenderTemplate.wounds,
                            attacks = defenderMeleeSnap.attacks,
                            baseHit = defenderMeleeSnap.hit,
                            normalDamage = defenderMeleeSnap.normalDamage,
                            criticalDamage = defenderMeleeSnap.criticalDamage,
                            rules = MeleeRuleParser.parse(defenderMeleeSnap.keywords)
                        )
                        var mState = MeleeCombatRules.start(mAttacker, mDefender, MeleeSide.ATTACKER)
                        mState = MeleeCombatRules.acceptRolls(
                            mState,
                            List(mAttacker.attacks) { idx -> if (idx == 0) 6 else 4 },
                            List(mDefender.attacks) { 4 }
                        )
                        if (mState.phase == MeleePhase.REROLL) {
                            val afterFirstPass = MeleeCombatRules.passReroll(mState, mState.rerollTurn)
                            mState = MeleeCombatRules.passReroll(afterFirstPass, afterFirstPass.rerollTurn)
                        }
                        mState = MeleeCombatRules.finishRetention(
                            mState,
                            MeleeRetainChoices(punishing = true, rending = true, severe = true),
                            MeleeRetainChoices(punishing = true, rending = true, severe = true)
                        )
                        var guard = 0
                        while (mState.phase == MeleePhase.RESOLVE && guard < 30) {
                            val turn = mState.resolveTurn
                            val pool = if (turn == MeleeSide.ATTACKER) mState.attackerPool else mState.defenderPool
                            val success = if (pool.criticals > 0) MeleeSuccess.CRITICAL else MeleeSuccess.NORMAL
                            mState = MeleeCombatRules.resolve(mState, turn, success, MeleeUse.STRIKE)
                            guard++
                        }
                        assertEquals("Melee combat did not finish for ${w.id} in ${team.id}", MeleePhase.FINISHED, mState.phase)
                    }
                }
            }
        }
    }

    @Test
    fun all48TeamsPlay24FullFourTurningPointBattles() {
        val teams = RosterCatalog.teams
        assertEquals(48, teams.size)

        teams.chunked(2).forEachIndexed { pairIdx, pair ->
            val teamA = pair[0]
            val teamB = pair[1]
            val rosterA = CanonicalRosters.forTeam(teamA.id)
            val rosterB = CanonicalRosters.forTeam(teamB.id)
            val eqA = TeamPloys.of(teamA.id, TeamPloys.FACTION_EQUIPMENT).take(2).joinToString("、") { it.name } + "、破片手雷、穿甲手雷"
            val eqB = TeamPloys.of(teamB.id, TeamPloys.FACTION_EQUIPMENT).take(2).joinToString("、") { it.name } + "、烟雾手雷、便携掩体"

            var battle = makeBattle(
                name = "第${pairIdx + 1}场：${teamA.name} vs ${teamB.name}",
                rosterA = rosterA,
                rosterB = rosterB,
                equipmentA = eqA,
                equipmentB = eqB
            )

            // Pre-battle deployment flow
            battle = BattleFlow.deployment(battle, BattleRules.SIDE_A)
            battle = battle.copy(progress = battle.progress.copy(setupStep = 2))
            repeat(6) {
                battle = BattleFlow.deployGroup(battle)
            }
            assertEquals(3, battle.progress.setupStep)

            val stratPloysA = TeamPloys.of(teamA.id, TeamPloys.STRATEGY)
            val stratPloysB = TeamPloys.of(teamB.id, TeamPloys.STRATEGY)
            val factionRulesA = TeamPloys.of(teamA.id, TeamPloys.FACTION_RULE)
            val firefightPloysA = TeamPloys.of(teamA.id, TeamPloys.FIREFIGHT)
            val firefightPloysB = TeamPloys.of(teamB.id, TeamPloys.FIREFIGHT)

            for (tp in 1..4) {
                assertEquals(BattlePhase.INITIATIVE, battle.phase)
                val winner = if (tp % 2 == 1) BattleRules.SIDE_A else BattleRules.SIDE_B
                battle = BattleFlow.initiative(battle, winner = winner, chosen = winner)
                assertEquals(tp, battle.turningPoint)
                assertEquals(BattlePhase.STRATEGY, battle.phase)

                // Use a 0CP Faction Rule strategic gambit or 1CP Strategic Ploy then pass twice
                val firstSide = battle.progress.nextSide
                val firstPloy = if (firstSide == BattleRules.SIDE_A) {
                    if (tp == 1 && factionRulesA.isNotEmpty()) factionRulesA.first().name to 0
                    else stratPloysA[(tp - 1) % stratPloysA.size].name to 1
                } else {
                    stratPloysB[(tp - 1) % stratPloysB.size].name to 1
                }
                if (battle.side(firstSide).commandPoints >= firstPloy.second) {
                    battle = BattleFlow.plan(battle, firstPloy.first, firstPloy.second)
                }
                while (battle.progress.planPasses < 2) {
                    battle = BattleFlow.passPlan(battle)
                }
                battle = BattleRules.advancePhase(battle) // -> FIREFIGHT
                assertEquals(BattlePhase.FIREFIGHT, battle.phase)

                // Exercise Chaos Cult mutation in TP2 if KT-CHAOS_CULT is present
                if (tp == 2 && (teamA.id == "KT-CHAOS_CULT" || teamB.id == "KT-CHAOS_CULT")) {
                    val cultSide = if (teamA.id == "KT-CHAOS_CULT") BattleRules.SIDE_A else BattleRules.SIDE_B
                    val devotee = battle.side(cultSide).operatives.firstOrNull { it.operativeId == "chaos_cult_05" && !it.incapacitated }
                    val mutantTemplate = RosterCatalog.operative(RosterCatalog.team("KT-CHAOS_CULT"), "chaos_cult_06")
                    if (devotee != null && mutantTemplate != null) {
                        battle = BattleFlow.replaceDatacard(battle, cultSide, devotee.memberId, mutantTemplate, keepLost = true, reason = "混沌异变")
                    }
                }

                // Exercise Gellerpox Mutoid Vermin summoning in TP1 if KT-GELLERPOX_INFECTED is present
                if (tp == 1 && (teamA.id == "KT-GELLERPOX_INFECTED" || teamB.id == "KT-GELLERPOX_INFECTED")) {
                    val gpSide = if (teamA.id == "KT-GELLERPOX_INFECTED") BattleRules.SIDE_A else BattleRules.SIDE_B
                    val cursemite = RosterCatalog.operative(RosterCatalog.team("KT-GELLERPOX_INFECTED"), "gellerpox_infected_07")
                    if (cursemite != null) {
                        battle = BattleFlow.addOperative(battle, gpSide, cursemite, ready = true, countsForKill = false, reason = "变异害虫阵营装备")
                    }
                }

                // Activate every ready operative in alternating order
                var guard = 0
                while ((BattleFlow.ready(battle, BattleRules.SIDE_A) || BattleFlow.ready(battle, BattleRules.SIDE_B)) && guard < 60) {
                    guard++
                    val sideId = battle.progress.nextSide
                    val readyOp = battle.side(sideId).operatives.firstOrNull { it.ready && !it.incapacitated }
                    if (readyOp == null) {
                        battle = BattleFlow.skipReaction(battle)
                        continue
                    }
                    battle = BattleFlow.activate(battle, sideId, readyOp.memberId, BattleFlow.effectiveApl(readyOp), engage = true)

                    // Perform a Unique Action if this operative has one with cost <= remaining AP, else a standard action
                    val activeState = battle.progress.active!!
                    val uniqueAction = AbilityCatalog.forOperative(readyOp.operativeId)
                        .firstOrNull { it.apCost != null && it.apCost in 1..(activeState.limit - activeState.spent) }
                    if (uniqueAction != null) {
                        battle = BattleFlow.action(battle, uniqueAction.name, uniqueAction.apCost ?: 1, "")
                    } else if (activeState.limit - activeState.spent >= 1) {
                        battle = BattleFlow.action(battle, "转移", 1, "")
                    }

                    // Inflict 4 wounds on an enemy operative during activations 1..4 to exercise damage, incapacitation, and Kill Op sync
                    val enemySideId = BattleFlow.other(sideId)
                    val enemyTarget = battle.side(enemySideId).operatives.firstOrNull { !it.incapacitated }
                    if (enemyTarget != null && guard <= 4) {
                        val nextWounds = (enemyTarget.currentWounds - 4).coerceAtLeast(0)
                        battle = updateWounds(battle, enemySideId, enemyTarget.memberId, nextWounds)
                    }

                    // Use a Firefight Ploy once in TP2
                    if (tp == 2 && guard == 1 && battle.side(sideId).commandPoints >= 1) {
                        val ffPloy = if (sideId == BattleRules.SIDE_A) firefightPloysA.first().name else firefightPloysB.first().name
                        battle = BattleFlow.firefightPloy(battle, sideId, ffPloy, 1)
                    }

                    battle = BattleFlow.endActivation(battle)
                }

                // Advance to next Turning Point (or COMPLETE at TP4)
                battle = BattleRules.advancePhase(battle)
            }

            assertTrue("Battle ${battle.name} should be completed after TP4", battle.completed)
            assertEquals(BattlePhase.COMPLETE, battle.phase)
        }
    }

    @Test
    fun hierotekCircleReanimationProtocolsReducesOpponentKillLevelAndVp() {
        val hierotekRoster = CanonicalRosters.forTeam("KT-HIEROTEK_CIRCLE")
        val opponentRoster = CanonicalRosters.forTeam("IMP-AOD")
        var battle = makeBattle("Hierotek Reanimation Test", opponentRoster, hierotekRoster)

        // Hierotek Circle has 8 operatives on the roster, but per 重生规程 opponent always uses startingCount = 5 (1 kill = Grade 1)
        val victim1 = battle.sideB.operatives[3]
        battle = updateWounds(battle, BattleRules.SIDE_B, victim1.memberId, 0)
        assertEquals("1 Hierotek kill should reach Kill Level 1 (starting count 5)", 1, battle.progress.killLevelA)
        assertEquals("1 Hierotek kill should award 1 Kill VP to Side A", 1, battle.sideA.score.kill)

        // When the Hierotek operative reanimates (wounds > 0), Side A's Kill Level and Kill VP drop back to 0!
        battle = updateWounds(battle, BattleRules.SIDE_B, victim1.memberId, 4)
        assertEquals("Reanimating Hierotek operative must reduce Kill Level back to 0", 0, battle.progress.killLevelA)
        assertEquals("Reanimating Hierotek operative must reduce Kill VP back to 0", 0, battle.sideA.score.kill)
    }

    @Test
    fun batch1HeadToHeadRuleFixesVerified() {
        // 1. Silent weapon allows first shoot from Conceal, blocks second shoot in same activation
        val pathRoster = CanonicalRosters.forTeam("KT-PATHFINDERS")
        val fellRoster = CanonicalRosters.forTeam("CHAOS-FELL")
        var b = makeBattle("Batch 1 Rules Test", pathRoster, fellRoster)
        b = BattleFlow.initiative(b, BattleRules.SIDE_A, BattleRules.SIDE_A)
        b = BattleRules.advancePhase(b)
        val opA = b.sideA.operatives.first()
        b = BattleFlow.activate(b, BattleRules.SIDE_A, opA.memberId, 3, engage = false)
        // First shoot with "安静" from Conceal succeeds
        b = BattleFlow.action(b, "射击", 1, "安静")
        // Second shoot with "安静" in the same activation must fail unless a double-shoot rule is given
        val secondShoot = runCatching { BattleFlow.action(b, "射击", 1, "安静") }
        assertTrue("Second shoot with only '安静' must be rejected", secondShoot.isFailure)

        // 2. CHAOS-FELL Frenzy token counts toward Kill Op and auto-incapacitates at end of activation
        val fellOp = b.sideB.operatives.first()
        b = BattleFlow.side(b, b.sideB.copy(operatives = b.sideB.operatives.map { o ->
            if (o.memberId == fellOp.memberId) {
                o.copy(tokens = o.tokens + OperativeToken(name = "狂暴", duration = TokenDuration.UNTIL_REMOVED))
            } else o
        }))
        assertEquals("Frenzied operative must count toward killIncapacitatedCount", 1, b.sideB.killIncapacitatedCount)
        b = BattleFlow.endActivation(b)
        b = BattleFlow.activate(b, BattleRules.SIDE_B, fellOp.memberId, 2, engage = false)
        assertEquals("Frenzied operative must be forced to Engage order", BattleOperativeState.ORDER_ENGAGE,
            b.sideB.operatives.first { it.memberId == fellOp.memberId }.order)
        b = BattleFlow.endActivation(b)
        assertTrue("Frenzied operative must be incapacitated when its activation ends",
            b.sideB.operatives.first { it.memberId == fellOp.memberId }.incapacitated)

        // 3. Explosive (爆炸物) does NOT block attack rerolls (愚笨 blocks ploys/equipment instead) and has dedicated glossary definition
        val expRules = ShootingRuleParser.parse(listOf("爆炸 1\"", "爆炸物", "有限 1"))
        assertEquals("爆炸物 must not block attack rerolls", false, expRules.noReroll)
        assertEquals("爆炸物", RuleGlossary.definitionFor("爆炸物")?.name)

        // 3b. APL token is read by BattleFlow.effectiveApl, and 标记光 is reduced by 1 on first movement action
        val opWithAplToken = opA.copy(tokens = listOf(
            OperativeToken(name = "APL", value = 1, duration = TokenDuration.NEXT_ACTIVATION_END),
            OperativeToken(name = "标记光", value = 2, duration = TokenDuration.UNTIL_REMOVED)
        ))
        assertEquals("APL +1 token must increase effectiveApl from 2 to 3", 3, BattleFlow.effectiveApl(opWithAplToken))

        // 3c. Frenzied defender in shooting survives a single normal hit at 1W, and dies to 2 normal hits or 1 crit
        val frenziedDefender = ShootingDefender("B", "b2", "P2", "Fellgor", 10, 3, 5, frenzied = true)
        val normAttacker = ShootingAttacker("A", "a2", "P1", "Shooter", "Rifle", 8, 8, 4, 3, 4, 5)
        var fState = ShootingCombatRules.start(normAttacker, frenziedDefender)
        fState = fState.copy(
            phase = ShootingPhase.DAMAGE,
            pendingHits = listOf(PendingHit(ShootingSuccess.NORMAL, 4, 4))
        )
        fState = ShootingCombatRules.applyDamage(fState)
        assertEquals("Frenzied defender hit by 1 normal die (4 dmg vs 3W) must survive at 1W", 1, fState.defender.currentWounds)

        // 4. Reduce piercing without criticals (盛装丑角 / 鳞盾)
        val pierceAttacker = ShootingAttacker("A", "a1", "P1", "Gunner", "Plasma", 10, 10, 4, 3, 4, 5,
            rules = ShootingRuleParser.parse(listOf("穿刺 1")))
        val clownDefender = ShootingDefender("B", "b1", "P2", "Player", 8, 8, 4, reducePiercingWithoutCrit = true)
        var sState = ShootingCombatRules.start(pierceAttacker, clownDefender)
        sState = ShootingCombatRules.acceptAttackRoll(sState, listOf(4, 4, 2, 1))
        sState = ShootingCombatRules.finishAttackRerolls(sState)
        sState = ShootingCombatRules.finishAttackRetention(sState)
        assertEquals("Without criticals, Piercing 1 is reduced by 1 -> 3 defence dice", 3, sState.defenceDiceToRoll)

        // 5. Duplicate Kasrkin Gunner and Void Dancer Player operatives get distinct default weapons
        val kasRoster = CanonicalRosters.forTeam("KT-KASRKIN")
        val vdtRoster = CanonicalRosters.forTeam("KT-VOID_DANCER_TROUPE")
        val bKasVdt = makeBattle("Kasrkin vs VDT", kasRoster, vdtRoster)
        val gunnerWeaponNames = bKasVdt.sideA.operatives.filter { it.operativeId == "kt_kas_gunner" }
            .map { it.weapons.first().name }.distinct()
        assertEquals("2 Kasrkin Gunners must have 2 distinct special weapons", 2, gunnerWeaponNames.size)
        val vdtPlayerWeapons = bKasVdt.sideB.operatives.filter { it.operativeId == "void_dancer_troupe_03" }
            .map { it.weapons.map { w -> w.name } }
        assertTrue("Every Void Dancer Player must have 2 weapons (1 ranged + 1 melee)", vdtPlayerWeapons.all { it.size == 2 })

        // 6. Zero .webp duplicates in TeamPloys across all 48 teams
        RosterCatalog.teams.forEach { team ->
            listOf(TeamPloys.STRATEGY, TeamPloys.FIREFIGHT, TeamPloys.FACTION_RULE).forEach { cat ->
                val ploys = TeamPloys.of(team.id, cat)
                assertTrue("No .webp card should appear in ${team.id} $cat", ploys.none { it.card?.endsWith(".webp") == true })
            }
        }
    }
}
