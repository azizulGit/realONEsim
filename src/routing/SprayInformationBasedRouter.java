/* 
 * Modified by AzizulAli
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import core.Settings;
import core.Tuple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import core.Connection;
import core.DTNHost;
import core.Message;


/**
 
 */
public class SprayInformationBasedRouter extends ActiveRouter {
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
    public static final String SPRAYINFOBASED_NS = "SprayInformationBasedRouter"; 
    public static final String NROF_COPIES = "nrofCopies";
    public static final String MSG_COUNT_PROPERTY = SPRAYINFOBASED_NS + "." + "copies";
    
    protected HashSet<String> finishedMessages;
    protected int initialNrofCopies;
    protected HashMap<Integer, Integer> utilities; 
	
    public SprayInformationBasedRouter(Settings s) {
		super(s);
		
		Settings sibrSettings = new Settings(SPRAYINFOBASED_NS);
		
		initialNrofCopies = sibrSettings.getInt(NROF_COPIES);
		
        utilities = new HashMap<Integer,Integer>();
    	finishedMessages = new HashSet<String>();

		//TODO: read&use epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected SprayInformationBasedRouter(SprayInformationBasedRouter r) {
		super(r);
		
		this.initialNrofCopies = r.initialNrofCopies;
		
        utilities = new HashMap<Integer,Integer>();
        finishedMessages = new HashSet<String>(r.finishedMessages);
		//TODO: copy epidemic settings here (if any)
	}


    @Override
    public SprayInformationBasedRouter replicate(){
        return new SprayInformationBasedRouter(this);
    }

    
	@Override
	public int receiveMessage(Message m, DTNHost from)
	{
		if(finishedMessages.contains(m.getId()))
			return DENIED_OLD;
		return super.receiveMessage(m, from);
	}
    
	
	@Override
	protected int startTransfer(Message m, Connection con)
	{
		int retVal = super.startTransfer(m, con);
		if(retVal == DENIED_OLD)
		{
			String id = m.getId();
			finishedMessages.add(id);
			if(hasMessage(id))
				deleteMessage(id, false);
		}
		return retVal;
	}

	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message m = super.messageTransferred(id, from);
		if(isDeliveredMessage(m))
			finishedMessages.add(m.getId());
		return m;
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
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>(); 
		
		Collection<Message> msgCollection = getMessageCollection();
			
			/* for all connected hosts collect all messages that have a higher
			   probability of delivery by the other host */
		for (Connection con : getConnections()) {
				DTNHost other = con.getOtherNode(getHost());
				SprayInformationBasedRouter othRouter = (SprayInformationBasedRouter)other.getRouter();
				
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
	
	    	return tryMessagesForConnected(messages);
	}


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