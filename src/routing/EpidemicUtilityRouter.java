/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import core.Settings;
import core.Tuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;


/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class EpidemicUtilityRouter extends ActiveRouter {
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
    public static final String EPIDEMICUTILITY_NS = "EpidemicUtilityRouter"; 
    public static final String NROF_COPIES = "nrofCopies";
    public static final String MSG_COUNT_PROPERTY = EPIDEMICUTILITY_NS + "." + "copies";
    
    
    protected int initialNrofCopies;
    protected HashMap<Integer, Integer> utilities; 
	
    public EpidemicUtilityRouter(Settings s) {
		super(s);
        utilities = new HashMap<Integer,Integer>();

		//TODO: read&use epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EpidemicUtilityRouter(EpidemicUtilityRouter r) {
		super(r);
        utilities = new HashMap<Integer,Integer>();
		//TODO: copy epidemic settings here (if any)
	}


    @Override
    public EpidemicUtilityRouter replicate(){
        return new EpidemicUtilityRouter(this);
    }


    @Override
    public void changedConnection(Connection con){
        super.changedConnection(con);

        if (con.isUp()){
            int nContacts = 0;
            DTNHost otherNode = con.getOtherNode (getHost());
            int otherAddress = otherNode.getAddress();

            if (utilities.containsKey(otherAddress)){
                nContacts = utilities.get(otherAddress);
            }

            utilities.put(otherAddress,nContacts + 1);
        }
    }


    @Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}
			
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		// then try any/all message to any/all connection
        tryOtherMessages();
	}


	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}
		
		return new ArrayList<Message>();
	}
	

		private Tuple<Message, Connection> tryOtherMessages() {
			List<Tuple<Message, Connection>> messages = 
				new ArrayList<Tuple<Message, Connection>>(); 
		
			Collection<Message> msgCollection = getMessageCollection();
			
			/* for all connected hosts collect all messages that have a higher
			   probability of delivery by the other host */
			//for(Connection con : getHost()) {
			for (Connection con : getConnections()) {
				DTNHost other = con.getOtherNode(getHost());
				EpidemicUtilityRouter othRouter = (EpidemicUtilityRouter)other.getRouter();
				
				if (othRouter.isTransferring()) {
					continue; // skip hosts that are transferring
				}
				
				for (Message m : msgCollection) {
					if (othRouter.hasMessage(m.getId())) {
						continue; // skip messages that the other one has
					}
	            
	            int destination = m.getTo().getAddress();
	
	            if (getCopiesLeft(m) > 1 && othRouter.getUtility(destination) > getUtility(destination)){
	                messages.add(new Tuple<Message,Connection>(m,con));
	            }
	        }
	    }
	
	    if (messages.isEmpty()){
	        return null;
	    }
	
	    return tryMessagesForConnected(messages);}


		protected int getUtility (int address){
			int utility = 0;

			if (utilities.containsKey(address)){
				utility = utilities.get(address);
			}
			return utility;
		}    

		protected int getCopiesLeft(Message m){
				Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
				assert nrofCopies != null : "This message" + m + "didn't have" + "nrof copies property!";

				return nrofCopies; 
		}
                
 }