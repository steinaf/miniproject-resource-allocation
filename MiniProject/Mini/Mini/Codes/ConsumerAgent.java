/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This File contains the Class ConsumerAgent, which is the extension
of Class Agent. ConsumerAgent has following Behaviours :

ReceivePropose--Receives the PROPOSE message from BrokerAgent
ReceiveRefuse---Receives the REFUSE message from BrokerAgent
ReceiveInform---Receives the INFORM message from ProviderAgent
ReceiveConfirm--Receives the CONFIRM message from ProviderAgent

*****************************************************************/

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;
import java.text.*;
import java.io.*;

public class ConsumerAgent extends Agent {

	private Resource resourceList;
	private int costLimit;
	// private long pl; // Resource Usage Length
	private int cost; // Cost agreed to

	private boolean proposalAccepted; // Status of Proposal

	// The list of known broker agents
	private AID[] brokerAgents;
	// The sellerAgent we are dealing with
	private AID sellerAgent;
	// The broker we are dealing with
	private AID broker;

	Behaviour receivePropose;
	Behaviour receiveRefuse;
	Behaviour receiveInform;
	Behaviour receiveConfirm;

	Date sentConfirm;

	// Put agent initializations here
	protected void setup() {

		System.out.println("Hello! Consumer-agent "+getAID().getName()+" is ready.");

		proposalAccepted = false;

		// Get the Information of the Resource to buy as a start-up argument

		Object[] args = getArguments();
		if (args != null && args.length > 0) {

			/** Argument Pattern :
			 * costLimit, R1.name, R1.plTime, R1.quantity
			 */

			costLimit = Integer.parseInt((String) args[0]);
			//pl = Long.parseLong((String) args[2]);

			resourceList = new Resource();

			resourceList.name = (String) args[1];
			resourceList.plTime = Integer.parseInt((String) args[2]);
			resourceList.quantity = Integer.parseInt((String) args[3]);
			resourceList.cost = 0;

			addBehaviour(new OneShotBehaviour() {

				public void action() {

					// Update the list of broker agents
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("ResourceBrokerAgent");
					template.addServices(sd);

					try {
						DFAgentDescription[] result = DFService.search(myAgent, template); 
						System.out.println(myAgent.getAID().getName()+" Found the following Broker agents:");
						brokerAgents = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							brokerAgents[i] = result[i].getName();
							System.out.println(myAgent.getAID().getName()+brokerAgents[i].getName());
						}

						// Choose a random broker to deal with

						Random generator = new Random();
						broker = brokerAgents[generator.nextInt(result.length)];
						System.out.println(myAgent.getAID().getName()+" Chosen Broker "+broker.getName());
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					// Send the CFP
					myAgent.addBehaviour(new SendCFP());
					// Start Listeners 
					myAgent.addBehaviour(receivePropose = new ReceivePropose());
					myAgent.addBehaviour(receiveRefuse = new ReceiveRefuse());
					myAgent.addBehaviour(receiveInform = new ReceiveInform());
				}
			} );
		}
		else {
			// Make the agent terminate
			System.out.println("No target Resource specified");
			doDelete();
		}
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Consumer-agent "+getAID().getName()+" terminating.");
	}

	public class SendCFP extends OneShotBehaviour {

		public void action() {

			ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

			cfp.addReceiver(broker); // Add broker to cfp recepient
			cfp.setConversationId("initital-cfp");

			// Add the Resource List to Content of message

			cfp.setContent(resourceList.name + ";" + Integer.toString(resourceList.plTime) + ";" + Integer.toString(resourceList.quantity));

			myAgent.send(cfp); //Send CFP to chosen Broker
			System.out.println(myAgent.getAID().getName()+" Sent CFP to "+broker.getName());

		}
	} // SendCFP

	public class ReceivePropose extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {

				System.out.println(myAgent.getAID().getName()+" PROPOSE Received");

				// Create reply message
				ACLMessage reply = msg.createReply();

				// Received a PROPOSE from Broker
				// Check if within cost limit

				cost = Integer.parseInt((String) msg.getContent());

				if (cost <= costLimit) {
					// Set to ACCEPT_PROPOSAL

					proposalAccepted = true; 

					reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);

					System.out.println(myAgent.getAID().getName()+" Sent ACCEPT_PROPOSAL");

				}
				else {
					// Set to REJECT_PROPOSAL

					proposalAccepted = false;

					reply.setPerformative(ACLMessage.REJECT_PROPOSAL);

					System.out.println(myAgent.getAID().getName()+" Sent REJECT_PROPOSAL");
				}

				// Send the reply to Broker
				myAgent.send(reply);

			}
			else {
				block();
				// Wait till correct message arrives
			}
		} // action()
	}

	public class ReceiveInform extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {

				System.out.println(myAgent.getAID().getName()+" Received INFORM from "+msg.getSender().getName());

				// Received an INFORM from Provider

				// Send CONFIRM to Provider if expecting INFORM else ignore

				if (proposalAccepted == true)
				{
					// Expecting INFORM

					ACLMessage reply = msg.createReply();

					reply.setPerformative(ACLMessage.CONFIRM);

					// Start timer for timeToComplete
					sentConfirm = new Date();
					myAgent.send(reply);

					System.out.println(myAgent.getAID().getName()+" Sent CONFIRM to "+msg.getSender().getName());
					SimpleDateFormat formatter= new SimpleDateFormat("yyyy/MMM/dd HH:mm:ss");
  					String dateNow = formatter.format(sentConfirm.getTime());				
					System.out.println(myAgent.getAID().getName()+" Task STARTS at  "+dateNow);
					// Sent CONFIRM
					// Stop waiting for all messages

					removeBehaviour(receivePropose);
					removeBehaviour(receiveRefuse);

					// Start listening for CONFIRM (signifying job is done)

					addBehaviour(receiveConfirm = new ReceiveConfirm());
				}

			}
			else {
				block();
				// Wait till correct message arrives
			}
		} // action()
	}

	public class ReceiveRefuse extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REFUSE);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {

				System.out.println(myAgent.getAID().getName()+" Received REFUSE from "+msg.getSender().getName());

				// On Receiving a REFUSE message, send CFP to a new broker and stop waiting for messages

				try {
				Thread.sleep(30);
				} catch (InterruptedException e)
				{
					System.out.println ("Exception");
				}

				addBehaviour(new SendCFP());

				proposalAccepted = false;
			}
			else {
				block();
				// Wait till correct message arrives
			}
		} // action()
	}

	public class ReceiveConfirm extends CyclicBehaviour {

		public void action() {

			removeBehaviour(receiveInform);

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {

				System.out.println(myAgent.getAID().getName()+" Received CONFIRM from "+msg.getSender().getName());
				System.out.println(myAgent.getAID().getName()+" Item Purchased from "+msg.getSender().getName());
				sellerAgent = msg.getSender();

				// Received CONFIRM 
				// Requested Job is complete

				// Calculate TimeToComplete

				Date receivedConfirm = new Date();

				long timeToComplete = ( receivedConfirm.getTime() - sentConfirm.getTime() ) / 1000; //in seconds
								
				SimpleDateFormat formatter= new SimpleDateFormat("yyyy/MMM/dd HH:mm:ss");
  				String dateNow = formatter.format(receivedConfirm.getTime());				
				System.out.println(myAgent.getAID().getName()+" Task FINISHED at  "+dateNow);
				// Calculate Utility

				long pl;

				pl = resourceList.plTime;

				double utility = (double) pl / timeToComplete ; // Maybe consider sigma-quantity / cost

				// Send Feedback to all Brokers

				ACLMessage feedback = new ACLMessage(ACLMessage.INFORM);
				feedback.setConversationId("Feedback");

				feedback.setContent(sellerAgent.getName()+";"+Double.toString(utility)); // sellername;utility

				for (int i = 0; i < brokerAgents.length; ++i) {
					feedback.addReceiver(brokerAgents[i]);
				}

				System.out.println(myAgent.getAID().getName()+" Feedback sent "+sellerAgent.getName()+";"+Double.toString(utility)); // sellername;utility

				myAgent.send(feedback);

				// Shutdown Agent

				myAgent.doDelete();
			}
			else {
				block();
				// Wait till correct message arrives.
			}
		} // action()
	}
}


