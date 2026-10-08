package dcbb.core.engine

/** Things that happened, for the UI log and for simulation statistics. */
sealed interface GameEvent {
    val text: String

    data class RoundStarted(val round: Int) : GameEvent {
        override val text get() = "— Round $round —"
    }

    data class CardPlayed(val defId: String, val name: String, val attuned: Boolean, val borrowed: Int) : GameEvent {
        override val text get() = buildString {
            append("You play $name")
            if (attuned) append(" (Attuned)")
            if (borrowed > 0) append(" — borrowed $borrowed")
        }
    }

    data class SignatureUsed(val name: String) : GameEvent {
        override val text get() = "You use $name"
    }

    data class DamageToEnemy(val enemyUid: Int, val enemyName: String, val amount: Int, val blocked: Int) : GameEvent {
        override val text get() = if (blocked > 0) "$enemyName takes $amount ($blocked blocked)" else "$enemyName takes $amount"
    }

    data class EnemyDied(val enemyUid: Int, val enemyName: String) : GameEvent {
        override val text get() = "$enemyName is defeated"
    }

    data class DamageToPlayer(val amount: Int, val blocked: Int, val source: String) : GameEvent {
        override val text get() = if (blocked > 0) "$source hits you for $amount ($blocked blocked)" else "$source hits you for $amount"
    }

    data class HpLost(val amount: Int, val reason: String) : GameEvent {
        override val text get() = "You lose $amount HP ($reason)"
    }

    data class Healed(val amount: Int) : GameEvent {
        override val text get() = "You restore $amount HP"
    }

    data class BlockGained(val amount: Int) : GameEvent {
        override val text get() = "You gain $amount Block"
    }

    data class IntentSet(val enemyName: String, val label: String, val countdown: Int) : GameEvent {
        override val text get() = "$enemyName prepares: $label (${countdown})"
    }

    data class IntentResolved(val enemyName: String, val label: String, val pressure: Int) : GameEvent {
        override val text get() = if (pressure > 0) "$enemyName: $label (Pressure $pressure)" else "$enemyName: $label"
    }

    data class Delayed(val label: String, val by: Int, val pressure: Int, val isIntent: Boolean) : GameEvent {
        override val text get() = if (isIntent) "Delayed $label by $by (Pressure $pressure)" else "Delayed your $label by $by"
    }

    data class IntentRemoved(val label: String) : GameEvent {
        override val text get() = "Unwritten: $label"
    }

    data class Scheduled(val label: String, val countdown: Int) : GameEvent {
        override val text get() = "Scheduled: $label ($countdown)"
    }

    data class VowBroken(val name: String) : GameEvent {
        override val text get() = "Your $name breaks! Penance: lose 4 HP"
    }

    data class Borrowed(val amount: Int) : GameEvent {
        override val text get() = "You borrow $amount energy from next turn"
    }

    data class ParadoxChanged(val from: Int, val to: Int) : GameEvent {
        override val text get() = "Paradox $from → $to"
    }

    data object Unravel : GameEvent {
        override val text get() = "PARADOX 10 — the Weave tears around you: your Present is Misprinted and a Loose End crawls through"
    }

    data class Info(override val text: String) : GameEvent
}
