package activitystreamer.server;

import org.json.simple.JSONObject;

public class AckCount {
    private int ackCount;
    //private String localPort;
    //private String localHost;
    private JSONObject message;
    public AckCount(JSONObject message, int ackCount){
        //this.localHost=localHost;
        //this.localPort=localPort;
        this.message = message;
        this.ackCount=ackCount;
    }

    public int getAckCount() {
        return ackCount;
    }
    public JSONObject getMessage(){
        return this.message;
    }
    public String getClientId() {
        return this.message.get("clientId").toString();
    }

    public String getMessageId() {
        if(this.message.get("messageId")!=null)
            return this.message.get("messageId").toString();
        else
            return null;
    }
    public void addAckCount(){
         this.ackCount++;
    }
}
