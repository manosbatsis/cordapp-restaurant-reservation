package com.template

import net.corda.core.contracts.*
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val RESERVATION_CONTRACT_ID = "com.template.ReservationContract"

class ReservationContract : Contract {
    // Our Create command.
    class Create : CommandData

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Create>()

        requireThat {
            // Constraints on the shape of the transaction.
            "No inputs should be consumed when issuing a reservation." using (tx.inputs.isEmpty())
            "There should be one output state of type ReservationState." using (tx.outputs.size == 1)

            // Reservation-specific constraints.
            val out = tx.outputsOfType<ReservationState>().single()
            "The number of persons must be greater than zero." using (out.persons > 0)

            // Constraints on the signers.
            "There must be two signers." using (command.signers.toSet().size == 2)

            // Other checks: available capacity etc.
        }
    }
}

// *********
// * State *
// *********
// Records the shared fact that a guest has reserved a table to a restaurant,
// suitable to seat the specified number of persons
data class ReservationState(val persons: Int,
                            val restaurant: Party,
                            val guest: Party) : ContractState {
    override val participants get() = listOf(restaurant, guest)
}