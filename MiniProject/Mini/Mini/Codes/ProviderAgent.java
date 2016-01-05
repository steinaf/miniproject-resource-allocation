/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This File contains the Class ProviderAgent, which is the extension
of Class Agent. ProviderAgent has following Behaviours :

ReceiveCFP--------Receives CFP from BrokerAgent
ReceiveConfirm----Receives CONFIRM from ConsumerAgent
TaskProcess-------Runs the Task in TaskQueue and Updates the Cost


*****************************************************************/

import jade.core.AID;
import jade.core.Agent;
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

public class ProviderAgent extends Agent {
	// The catalogue of Resource for sale (maps the title of a Resource to its price and Demand-Ratio)
	private Hashtable catalogue;
	private Hashtable cfpList;
	private ProviderGui myGui;
	private List taskQueue;

	// Stores the list of broker agents available.
	public AID[] brokerAgents;

	protected class TaskQ extends ResourceBP {
		public Date finishTime;
		public boolean running;
	}

	// Put agent initializations here

	protected void setup() {
		// Create the catalogue
		catalogue = new Hashtable();
		cfpList = new Hashtable();
		taskQueue = new Vector();

		// Create and show the GUI 
		myGui = new ProviderGui(this);
		myGui.showGui();

		// Register the provider agent in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("ResourceProviderAgent");
		sd.setName("JADE-trading");
		dfd.addServices(sd);

		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Add the behaviour
		addBehaviour(new ReceiveCFP());
		addBehaviour(new ReceiveConfirm());
		addBehaviour(new TaskProcess());

	} // setup()

	// Put agent clean-up operations here
	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Close the GUI
		myGui.dispose();
		// Printout a dismissal message
		System.out.println("Resource Provider-agent "+getAID().getName()+" terminating.");
	}

	public class CatalogueEntry {
		public int quantity;
		public int price;
		public double demandRatio;
		public int inUse;

		public CatalogueEntry(int qty, int pr, double dr, int inU){
			quantity = qty;
			price = pr;
			demandRatio = dr;
			inUse = inU;
		}
	}

	/**
	  This is invoked by the GUI when the user adds a new resource 
	  */

	public void addToCatalogue(final String name,final int quantity, final int price, final double demandRatio) {

		addBehaviour(new OneShotBehaviour() {

			public void action() {

				catalogue.put(name, new CatalogueEntry(quantity, price,demandRatio,0));	//Insert Resource into catalogue

				System.out.println(myAgent.getAID().getName()+ name+" inserted into catalogue. Quantity = "+quantity+" Price = "+price+" Demand Price Ratio = "+demandRatio);

				//Discover Brokers

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
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}

				//Register catalogueEntry with Brokers

				ACLMessage inform = new ACLMessage(ACLMessage.INFORM);

				for (int i = 0; i < brokerAgents.length; ++i) {
					inform.addReceiver(brokerAgents[i]);
				} 

				int cost = ((int)((1+demandRatio)*price));

				inform.setContent(name+";"+quantity+";"+cost);
				inform.setConversationId("Update");

				myAgent.send(inform);	// Send inform message

				System.out.println(myAgent.getAID().getName()+" Content Sent ="+name+";"+quantity+";"+cost);
			} 
		});
	}

	private class ReceiveCFP extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP received. Process it

				ResourceBP cfp = new ResourceBP(); 
				cfp.resource = new Resource();
				// Parse message and make cfp object

				String msgContent = (String) msg.getContent();
				System.out.println(myAgent.getAID().getName()+msgContent);
				String[] tokens = msgContent.split(";");

			/*	for (String t : tokens)
				{
					System.out.println(myAgent.getAID().getName()+t);
				}
			*/
				cfp.consumerAID = new AID(tokens[0], AID.ISGUID);
				cfp.resource.name = new String(tokens[1]);
				cfp.resource.plTime = Integer.parseInt(tokens[2]);
				cfp.resource.quantity = Integer.parseInt(tokens[3]);
				cfp.resource.cost = Integer.parseInt(tokens[4]);
				//System.out.println(myAgent.getAID().getName()+"CFP = "+cfp.consumerAID);
				CatalogueEntry cat;
				boolean acceptable = true;

				// If ProposedCost >= CurrentCost

				cat =(CatalogueEntry) catalogue.get(cfp.resource.name);

				if ( cfp.resource.cost < cat.price )
				{
					acceptable = false;
				}

				if (acceptable == true)
				{
					// Add <ConsumerAID,CFP> to HashTable

					cfpList.put(cfp.consumerAID, cfp.resource);

					// Send PROPOSE to Broker

					ACLMessage prop = msg.createReply();
					prop.setPerformative(ACLMessage.PROPOSE);
					prop.setContent(cfp.consumerAID.getName());

					myAgent.send(prop);

					// Send INFORM to Consumer

					ACLMessage inf = new ACLMessage(ACLMessage.INFORM);

					inf.addReceiver(cfp.consumerAID);

					myAgent.send(inf);

					// Start waiting for CONFIRM from Consumer
				}
				else
				{
					// Update all brokers with current prices

					//Discover Brokers

					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("ResourceBrokerAgent");
					template.addServices(sd);

					try {
						DFAgentDescription[] result = DFService.search(myAgent, template); 
						brokerAgents = new AID[result.length];

						for (int i = 0; i < result.length; ++i) {
							brokerAgents[i] = result[i].getName();
						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}

					//Register catalogueEntry with Brokers

					ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
					inform.setConversationId("Update");

					for (int i = 0; i < brokerAgents.length; ++i) {
						inform.addReceiver(brokerAgents[i]);
					}

					Set catalogueSet = catalogue.entrySet();	
					Iterator it = catalogueSet.iterator();
					Map.Entry temp;

					while (it.hasNext())
					{
						temp =(Map.Entry) it.next();
						CatalogueEntry t =(CatalogueEntry) temp.getValue();

						int cost = ((int)((1+t.demandRatio)*t.price));
						inform.setContent(temp.getKey()+";"+t.quantity+";"+cost);
						myAgent.send(inform);	// Send inform message
					}

					// Send REFUSE to Broker 

					ACLMessage refuse = msg.createReply();
					refuse.setPerformative(ACLMessage.REFUSE);
					refuse.setContent(cfp.consumerAID.getName());

					myAgent.send(refuse);
				}
				// Done
			}
			else {
				block();
				// Wait for CFP
			}
		}
	}  // End of inner class ReceiveCFP

	private class ReceiveConfirm extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// Confirm received. Process it
				// Get ConsumerAID, lookup HashTable

				Resource resource = (Resource) cfpList.get(msg.getSender());

				// Update DemandPriceRatio for all resources requested

				CatalogueEntry res;
				res = (CatalogueEntry) catalogue.get(resource.name);
				res.demandRatio += 0.5;
				catalogue.put(resource.name, res);

				// Add task to task-queue

				TaskQ entry = new TaskQ();
				entry.resource = new Resource();
				entry.resource.name = resource.name;
				entry.resource.quantity = resource.quantity;
				entry.resource.plTime = resource.plTime;
				entry.resource.cost = resource.cost;
				entry.running = false;
				entry.consumerAID = msg.getSender();

				taskQueue.add(entry);

			}
			else {
				block();
				// Wait for CFP
			}
		}
	} // End ReceiveConfirm

	private class TaskProcess extends CyclicBehaviour {
		public void action() {
			// Check Task Queue for tasks
			Date currTime;

			for (int i = 0; i < taskQueue.size() ; ++i)
			{
				TaskQ temp = (TaskQ) taskQueue.get(i);
				// Find tasks which can be run

				if (temp.running == false)
				{
					// Check if can be run
					
					CatalogueEntry cat =(CatalogueEntry) catalogue.get(temp.resource.name);

					if ( temp.resource.quantity <= (cat.quantity - cat.inUse))
					{
						// Quantity needed <= Quantity available

						// Set task to running and set finishTime
						temp.running = true;
						temp.finishTime = new Date(System.currentTimeMillis() + ((long)temp.resource.plTime * 1000));

						taskQueue.set(i, temp);

						// Add quantity to inUse
						cat.inUse += temp.resource.quantity;
						catalogue.put(temp.resource.name, cat);
					}
				}
				else
				{
					// Is Time > finishTime

					currTime = new Date();
					if (currTime.after(temp.finishTime))
					{
						// Task is done

						// Free Resources
						CatalogueEntry cat = (CatalogueEntry) catalogue.get(temp.resource.name);
						cat.inUse -= temp.resource.quantity;
						//Reduce demandRatio
						cat.demandRatio -= 0.5;
						catalogue.put(temp.resource.name, cat);

						//CONFIRM to Consumer

						ACLMessage conf = new ACLMessage(ACLMessage.CONFIRM);
						conf.addReceiver(temp.consumerAID);

						myAgent.send(conf);

						//Remove from TaskQueue

						taskQueue.remove(i);
						i--;

						// Update all brokers with current prices

						//Discover Brokers

						DFAgentDescription template = new DFAgentDescription();
						ServiceDescription sd = new ServiceDescription();
						sd.setType("ResourceBrokerAgent");
						template.addServices(sd);

						try {
							DFAgentDescription[] result = DFService.search(myAgent, template); 
							brokerAgents = new AID[result.length];

							for (int j = 0; j < result.length; ++j) {
								brokerAgents[j] = result[j].getName();
							}
						}
						catch (FIPAException fe) {
							fe.printStackTrace();
						}

						//Register catalogueEntry with Brokers

						ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
						inform.setConversationId("Update");

						for (int k = 0; k < brokerAgents.length; ++k) {
							inform.addReceiver(brokerAgents[k]);
						}

						Set catalogueSet = catalogue.entrySet();	
						Iterator it = catalogueSet.iterator();
						Map.Entry tempE;

						while (it.hasNext())
						{
							tempE =(Map.Entry) it.next();
							CatalogueEntry t = (CatalogueEntry) tempE.getValue();

							int cost = ((int)((1+t.demandRatio)*t.price));
							inform.setContent(tempE.getKey()+";"+t.quantity+";"+cost);
							myAgent.send(inform);	// Send inform message
						}


					}
				}
			}
		}
	}

}
