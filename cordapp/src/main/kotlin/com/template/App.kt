package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.SignedTransaction
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.webserver.services.WebServerPluginRegistry
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.function.Function
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// *****************
// * API Endpoints *
// *****************
@Path("template")
class TemplateApi(val rpcOps: CordaRPCOps) {
    // Accessible at /api/template/templateGetEndpoint.
    @GET
    @Path("templateGetEndpoint")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok("Template GET endpoint.").build()
    }
}

// *********
// * Flows *
// *********
/**
 * Restaurant's response to ReservationFlow
 */
@InitiatedBy(ReservationFlow::class)
class ReservationFlowResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be a Reservation transaction." using (output is ReservationState)
                val Reservation = output as ReservationState
                "The Reservation persons number cannot be higher than the total seats capacity." using (Reservation.persons < 120)
            }
        }

        subFlow(signTransactionFlow)
    }
}

@InitiatingFlow
@StartableByRPC
// Constructor parameters:
// - personsValue, which is the value of the number of seats/persons for the table being reserved
// - guestParty, the guest making the reservation (the node running the flow is the restaurant side)
class ReservationFlow(private val personsValue: Int,
                      private val dateValue: ZonedDateTime,
                      private val guestParty: Party) : FlowLogic<Unit>() {

    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {
        // Every transaction requires a notary to prevent double-spends and serve as a timestamping authority.
        // The first thing we do in our flow is retrieve the a notary from the node’s ServiceHub.
        // ServiceHub.networkMapCache provides information about the other nodes on the network
        // and the services that they offer.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // We create a transaction builder.
        val txBuilder = TransactionBuilder(notary = notary)

        // We create the transaction components.
        val outputState = ReservationState(personsValue, dateValue, ourIdentity, guestParty)
        val outputContractAndState = StateAndContract(outputState, RESERVATION_CONTRACT_ID)
        val cmd = Command(ReservationContract.Create(), listOf(ourIdentity.owningKey, guestParty.owningKey))

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)

        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Creating a session with the other party.
        val otherpartySession = initiateFlow(guestParty)

        // Obtaining the counterparty's signature.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherpartySession), CollectSignaturesFlow.tracker()))

        // Finalising the transaction.
        subFlow(FinalityFlow(fullySignedTx))
    }
}

// ***********
// * Plugins *
// ***********
class TemplateWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::TemplateApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the templateWeb directory in resources to /web/template
            "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )
}

// Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// This class is not annotated with @CordaSerializable, so it must be added to the serialization whitelist, above, if
// we want to send it to other nodes within a flow.
data class TemplateData(val payload: String)
