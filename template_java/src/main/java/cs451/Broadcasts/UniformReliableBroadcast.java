package cs451.Broadcasts;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import cs451.Host;
import cs451.Parser;
import cs451.Links.Helper;

public class UniformReliableBroadcast extends Broadcast {
    ConcurrentLinkedQueue<String> log;
    BestEffortBroadcast bestEffortBroadcast;
    List<Host> hosts; //list of all processes
    Set<String> deliveredUid; // messages i have already delivered
    Set<String> forward; //msgs i have seen & bebBroadcast but not delivered yet, Strings contain rawData i.e (msg_uid + msg) 
    Parser parser;
    ConcurrentHashMap<String, Set<Integer>> ackedMuid; //(msg_uid, #acksReceivedforthis) //probably need a map
    FIFOBroadcast fifoBroadcast;

    public UniformReliableBroadcast(Parser parser, FIFOBroadcast caller) {
        //init
        this.fifoBroadcast = caller;
        this.parser = parser;
        log =  new ConcurrentLinkedQueue<String>();
        bestEffortBroadcast = new BestEffortBroadcast(parser, this);
        hosts = parser.hosts();
        //TODO could think a better datastructure instead of using three sets.
        //TODO implement cleanup of the sets.(garbage collection)
        ackedMuid = new ConcurrentHashMap<String, Set<Integer>>(); 
        deliveredUid = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        forward = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    public boolean broadcast(String msg_uid, String msg) throws IOException {
        String rawData = Helper.appendMsg(msg_uid, msg);
        forward.add(rawData); //add message to pending
        deliverIfCan(); //check if can deliver message (TODO check if this is usefull)
        bestEffortBroadcast.broadcast(msg_uid, msg);
        //log.add("b " + Helper.getSeqNumFromMessageUid(msg_uid));
        return true;

    }
    public boolean deliver(String rawData) throws IOException {
        //increment ack count in ackedMuid for Helper.getMsgUid(rawData)
        String msg_uid = Helper.getMsgUid(rawData);
        String proc_id = Helper.getProcIdFromMessageUid(msg_uid);
        ackedMuid.get(msg_uid).add(Integer.parseInt(proc_id)); 
        if(!forward.contains(rawData)){ //forward everything only once
            forward.add(rawData);
            bestEffortBroadcast.broadcast(Helper.getMsgUid(rawData), Helper.getMsg(rawData));
        }
        deliverIfCan();
        return true;
    }

    public boolean deliverIfCan() throws IOException {
        //Look in list of forward(pending) messages and send those who have been acked by more then half the hosts
        //and who have not been delivered before
        for(String rawData : forward) {
            String msg_uid = Helper.getMsgUid(rawData);
            Set<Integer> acksForMsgUid = ackedMuid.get(msg_uid);
            if(acksForMsgUid!= null && acksForMsgUid.size() > hosts.size()/2 && !deliveredUid.contains(msg_uid)){
                actuallyDeliver(rawData);
            }
        }
        return true;
    }

    public boolean actuallyDeliver(String rawData) throws IOException {
        //add msgUid to delivered messages set 
        deliveredUid.add(Helper.getMsgUid(rawData)); 
        //update log//URB DELIVER
        //log.add("d " + Helper.getProcIdFromMessageUid(Helper.getMsgUid(rawData)) 
        //    + " " + Helper.getSeqNumFromMessageUid(Helper.getMsgUid(rawData)));
        fifoBroadcast.deliver(rawData);
        return true;
    }

    public BestEffortBroadcast getBestEffortBroadcast() {
        return bestEffortBroadcast;
    }

    public ConcurrentLinkedQueue<String> getLogs() {
        return log;
    }
}
