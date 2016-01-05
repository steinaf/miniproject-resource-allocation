/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This File contains the Class BrokerAgent, which is the extension
of Class Agent. BrokerAgent has following Behaviours :

ReceivedInform----------Receives the INFORM message from ProveiderAgent
ReceiveCFP--------------Receives the CFP message from ConsumerAgent
ReceiveRefuse-----------Receives REFUSE message from ProviderAgent
ReceivePropose----------Receives PROPOSE message from ProviderAgent
ReceiveAcceptProposal---Receives ACCEPT_PROPOSAL message from ConsumerAgent
ReceiveRejectProposal---Receives REJECT_PROPOSAL message from ConsumerAgent

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

import java.io.*;
import java.util.*;
import java.text.*;

public class BrokerAgent extends Agent {

	private Hashtable catalogue; // ResourceName, SortedSet (Provider, Quantity, Cost, Quality) sorted as per quality
	private Hashtable cfpList; // ConsumerAID, resource somehow need to store which provider we chose also

	public class SetEntry {
		public AID provider;
		public int quantity;
		public int cost;
		public double quality;
	}

	public class Comp implements Comparator {

		public int compare(Object o1,Object o2) {
			if (o1.equals(o2)) return 0;
			SetEntry s1 = (SetEntry) o1;
			SetEntry s2 = (SetEntry) o2;

			if (s1.quality >= s2.quality)
				return -1;
			else
				return 1;
		}
	}

	private class CFPEntry extends Resource {
		public AID provider;
	}

	protected void setup() {

		// Create the catalogue
		catalogue = new Hashtable();
		cfpList = new Hashtable();

		// Register the Broker service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("ResourceBrokerAgent");
		sd.setName("JADE-trading");
		dfd.addServices(sd);

		try {
			DFService.register(this, dfd);
		}

		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Start listeners
		addBehaviour(new ReceivedInform());
		addBehaviour(new ReceiveCFP());
		addBehaviour(new ReceiveRefuse());
		addBehaviour(new ReceivePropose());
		addBehaviour(new ReceiveAcceptProposal());
		addBehaviour(new ReceiveRejectProposal());

	}

	protected void takeDown() {

		// Deregister from the yellow pages

		try {
			DFService.deregister(this);
		}

		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		System.out.println("Broker is Terminating at Point 1");
		System.out.println("Broker-agent "+getAID().getName()+" terminating.");
	}

	private class ReceivedInform extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				if ( msg.getConversationId().equals("Update"))
				{
					// Got a Update message from Provider
					String msgContent = (String) msg.getContent();
					String[] tokens = msgContent.split(";");

					// Check if entry is present

					if (!catalogue.containsKey((String) tokens[0]))
					{
						TreeSet set = new TreeSet(new Comp());

						SetEntry e = new SetEntry();
						e.cost = Integer.parseInt(tokens[2]);
						e.quantity = Integer.parseInt(tokens[1]);
						e.provider = msg.getSender();
						e.quality = 1.0;

						set.add(e);
						catalogue.put(tokens[0], set);
					}
					else
					{
						TreeSet set = (TreeSet) catalogue.get(tokens[0]);

						Iterator it = set.iterator();
						boolean found = false;

						while(it.hasNext() && !found)
						{
							SetEntry e = (SetEntry) it.next();

							if (e.provider == msg.getSender())
							{
								//Found entry
								found = true;

								set.remove(e);

								e.cost = Integer.parseInt(tokens[2]);

								set.add(e);

								catalogue.put(tokens[0], set);
							}
						}

						if (found == false)
						{
							// No Entry

							SetEntry e = new SetEntry();
							e.cost = Integer.parseInt(tokens[2]);
							e.quantity = Integer.parseInt(tokens[1]);
							e.provider = msg.getSender();
							e.quality = 1.0;

							set.add(e);
							catalogue.put(tokens[0], set);
						}
					}
				}
				else
				{
					// Got a Feedback message from Consumer

					String cont = (String) msg.getContent();
					String[] tokens = cont.split(";");

					AID prov = new AID(tokens[0], AID.ISLOCALNAME); 
					double qual = Double.parseDouble(tokens[1]);

					// Update quality of Provider

					Enumeration en = (Enumeration) catalogue.keys();
					String res;
					TreeSet set;

					while (en.hasMoreElements())
					{
						res = (String) en.nextElement();
						set = (TreeSet) catalogue.get(res);

						Iterator it = set.iterator();

						while(it.hasNext())
						{
							SetEntry e = (SetEntry) it.next();
							if (e.provider == prov)
							{
								e.quality = qual;
								set.remove(e);
								set.add(e);
							}
						}

					}
				}
			}
			else {
				block();
				// Wait for INFORM
			}
		}
	} // ReceiveInform


	private class ReceiveCFP extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {

				// Received a CFPEntry from Consumer

				// Parse it

				String msgContent = (String) msg.getContent();
				String[] tokens = msgContent.split(";");

				CFPEntry cfp = new CFPEntry();

				cfp.name = tokens[0];
				cfp.plTime = Integer.parseInt(tokens[1]);
				cfp.quantity = Integer.parseInt(tokens[2]);

				// Choose best provider on basis of quality and who has sufficient resource

				boolean found = false;
				SetEntry entry = new SetEntry();

				if (catalogue.containsKey(cfp.name))
				{
					TreeSet set = (TreeSet) catalogue.get(cfp.name);

					Iterator it = set.iterator();

					while( it.hasNext() )
					{
						entry = (SetEntry) it.next();

						if (cfp.quantity <= entry.quantity)
						{
							found = true;
							break;
						}
					}

					// If such a provider exists
					if (found)
					{
						// Calculate cost = quantity * cost * pl
						cfp.provider = entry.provider;
						cfp.cost = entry.cost;

						int proposeCost = cfp.cost * cfp.quantity * cfp.plTime;

						// Send PROPOSE with cost to Consumer

						ACLMessage prop = msg.createReply();
						prop.setPerformative(ACLMessage.PROPOSE);
						prop.setContent(Integer.toString(proposeCost));
						System.out.println(myAgent.getAID().getName()+" Broker: Sent PROPOSE:"+(Integer.toString(proposeCost)));

						myAgent.send(prop);

						// Store costs and cfp in cfpList
						cfpList.put(msg.getSender(), cfp);
					}
				}

				if (!found)
				{
					// Send REFUSE to Consumer

					ACLMessage ref = msg.createReply();
					ref.setPerformative(ACLMessage.REFUSE);

					myAgent.send(ref);
				}
			}

			else {
				block();
				// Wait for CFP
			}
		}
	}  // ReceiveCFP

	private class ReceivePropose extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				// Received Propose

				String consumer = (String) msg.getContent();
				AID consAID = new AID(consumer, AID.ISLOCALNAME);

				// Delete cfpEntry in cfpList

				if (cfpList.containsKey(consAID))
				{
					cfpList.remove(consAID);
				}
			}
			else {
				block();
				// Wait for PROPOSE
			}
		}
	} // ReceivePropose

	private class ReceiveRefuse extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REFUSE);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				// Refuse received from Provider
				// Should have updated costs before being sent Refuse
				// Calculate costs again and send proposal to customer

				String consumer = (String) msg.getContent();
				AID consAID = new AID(consumer, AID.ISLOCALNAME);


				CFPEntry cfp = (CFPEntry) cfpList.get(consAID);

				TreeSet set = (TreeSet) catalogue.get(cfp.name);

				Iterator it = set.iterator();

				SetEntry entry = new SetEntry();

				while(it.hasNext())
				{
					entry = (SetEntry) it.next();
					if ( entry.provider.equals(msg.getSender()));
					break;
				}

				cfp.cost = entry.cost;

				int proposeCost = cfp.cost * cfp.quantity * cfp.plTime;

				// Send PROPOSE with new cost to Consumer

				ACLMessage prop = new ACLMessage(ACLMessage.PROPOSE);
				prop.setContent(Integer.toString(proposeCost));
				System.out.println(myAgent.getAID().getName()+" Broker: Sent PROPOSE:"+(Integer.toString(proposeCost)));
				prop.addReceiver(consAID);

				myAgent.send(prop);

				cfpList.put(consAID, cfp);
			}
			else {
				block();
				// Wait for REFUSE
			}
		}
	} // ReceiveRefuse

	private class ReceiveAcceptProposal extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				// Proposal accepted by Customer
				// Forward CFP to Provider

				CFPEntry cfp = (CFPEntry) cfpList.get(msg.getSender());

				ACLMessage fwd = new ACLMessage(ACLMessage.CFP);

				fwd.addReceiver(cfp.provider);

				fwd.setContent(msg.getSender().getName() + ";" + cfp.name + ";" + Integer.toString(cfp.plTime) + ";" + Integer.toString(cfp.quantity) + ";" + Integer.toString(cfp.cost));
				System.out.println(myAgent.getAID().getName()+"Broker: Sent CFP:"+msg.getSender().getName() + ";" + cfp.name + ";" + Integer.toString(cfp.plTime) + ";" + Integer.toString(cfp.quantity) + ";" + Integer.toString(cfp.cost));

				myAgent.send(fwd);

			}
			else {
				block();
				// Wait for ACCEPT_PROPOSAL
			}
		}
	} // ReceiveAcceptProposal 

	private class ReceiveRejectProposal extends CyclicBehaviour {

		public void action() {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);

			if (msg != null) {
				// Proposal rejected by Customer due to high costs
				// Choose next best provider on basis of quality, calculate costs and send proposal to customer

				AID consAID = msg.getSender();

				CFPEntry cfp = (CFPEntry) cfpList.get(consAID);

				TreeSet set = (TreeSet) catalogue.get(cfp.name);

				Iterator it = set.iterator();

				SetEntry entry;

				while(it.hasNext())
				{
					entry = (SetEntry) it.next();
					if ( entry.provider.equals(msg.getSender()));
					break;
				}

				if (it.hasNext())
				{
					entry = (SetEntry) it.next();

					cfp.provider = entry.provider;
					cfp.cost = entry.cost;

					int proposeCost = cfp.cost * cfp.quantity * cfp.plTime;

					// Send PROPOSE with new cost to Consumer

					ACLMessage prop = new ACLMessage(ACLMessage.PROPOSE);
					prop.setContent(Integer.toString(proposeCost));
					System.out.println(myAgent.getAID().getName()+"Broker: Sent PROPOSE:"+Integer.toString(proposeCost));
					prop.addReceiver(consAID);

					myAgent.send(prop);

					// Update cfpList with new cost and provider					
					cfpList.put(consAID, cfp);
				}
				else
				{
					// Send REFUSE since no provider within cost limit

					ACLMessage ref = msg.createReply();
					ref.setPerformative(ACLMessage.REFUSE);

					// Remove CFP of consumer from cfpList
					cfpList.remove(consAID);
					myAgent.send(ref);
				}


			}
			else {
				block();
				// Wait for REJECT_PROPOSAL
			}
		}
	} // ReceiveRejectProposal 

}
