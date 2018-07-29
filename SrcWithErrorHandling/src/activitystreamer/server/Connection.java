package activitystreamer.server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Connection extends Thread {


    private boolean open = false;
    private Socket socket;
    private boolean flag = false;//True for server connection; False for server-client
    private boolean term = false;
    private boolean authenticatedUser = false;
    private boolean authenticatedServer = false;
    private String remoteHost;
    private int remotePort;
    private int id;
    private String username = null;
    private String secret = null;
    BufferedReader ind;
    BufferedWriter outd;
    Random rand = new Random(15);
    int infinity = 10000000;
    private static int messageId=0;


    public Connection(Socket socket, int id) throws IOException {
        ind = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        outd = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        this.socket = socket;
        open = true;
        this.id = id;
        start();
    }
    public Connection(Socket socket, int id,boolean flag) throws IOException {
        ind = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        outd = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        this.socket = socket;
        open = true;
        this.id = id;
        this.flag = flag;
        start();
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public boolean getFlag() {
        return flag;
    }

    public int getID() {
        return this.id;
    }

    public void setID(int id) {
        this.id = id;
    }

    public synchronized static Control getControl() {
        return Control.getInstance();
    }

    /*
     * returns true if the message was written, otherwise false
     */


    public void closeCon() {
        if (open) {
            try {
                socket.close();
                Control.getInstance().getConnections().remove(this);
            } catch (IOException e) {
                // already closed?
            }
        }
    }

    public ServerAddress tryRedirect() {
        int minLoad = infinity;
        int newPort = 0;
        String newAddress = "";
        ArrayList<ServerAddress> sa = Control.getInstance().getServerAddresses();
        try {
            for (int i = 0; i < sa.size(); i++) {
                if (sa.get(i).getLoad() < minLoad &&
                        (!sa.get(i).getIpAddress().equals(InetAddress.getLocalHost().getHostAddress())
                                || sa.get(i).getPort() != Control.getInstance().getListener().getPortnum())) {
                    minLoad = sa.get(i).getLoad();
                    newAddress = sa.get(i).getIpAddress();
                    newPort = sa.get(i).getPort();
                }
            }
        }catch (java.net.UnknownHostException ue)
        {
            System.out.println("Hostname Not Valid");
        }
        try {
            System.out.println(InetAddress.getLocalHost().getHostAddress() + "  " + Control.getInstance().getListener().getPortnum());
            if (Control.getInstance().getConnections().size() - minLoad >= 2) {
                ServerAddress re = new ServerAddress(newAddress, newPort);
                return re;
            }
        } catch (Exception e) {
            System.out.println("Hostname not found");
        }
        return null;
    }

    public void run() {
        String data;
        JSONObject command;
        JSONObject returnCommand = new JSONObject();
        LockCount lc;
        boolean destroyCon = false;
        try {
            if (!this.flag) {
                data = ind.readLine();
                ServerAddress sa;
                command = (JSONObject) (new JSONParser().parse(data));
                while (!command.get("command").toString().equals("LOGOUT") && !destroyCon) {
                    command = (JSONObject) (new JSONParser().parse(data));
                    System.out.println(command.get("command").toString());
                    switch (command.get("command").toString()) {
                        case "AUTHENTICATE":
                            if (!this.authenticatedServer) {
                                if (command.get("secret").toString().equals(Settings.getSecret())) {
                                    setFlag(true);
                                    this.socket.setSoTimeout(Settings.getTimeOut());
                                    ServerAddress outsa = new ServerAddress(command.get("hostname").toString(),
                                            Integer.parseInt(command.get("port").toString()),0);
                                    if(Control.getInstance().serverInList(command.get("hostname").toString(),Integer.parseInt(command.get("port").toString())) < 0)
                                        Control.getInstance().getServerAddresses().add(outsa);
                                    this.remoteHost = command.get("hostname").toString();
                                    this.remotePort = Integer.parseInt(command.get("port").toString());
                                    System.out.println("Connected with: " + outsa.getIpAddress()+ " " +outsa.getPort());
                                    Control.getInstance().getServerCons().add(this);
                                    this.setID(Control.getInstance().getServerCons().size());
                                    Control.getInstance().getConnections().remove(this);
                                    this.authenticatedServer = true;
                                    try {
                                        receiveLoad();
                                    }
                                    catch (SocketTimeoutException ste){
                                        destroyCon = true;
                                        System.out.println("Too long without a message from " + remoteHost + "  " + remotePort
                                                + " , waiting for its reconnection");
                                        Control.getInstance().removeServerCon(this);
                                        int serverIndex = Control.getInstance().serverInList(remoteHost,remotePort);
                                        if(serverIndex >= 0)
                                            Control.getInstance().getServerAddresses().get(serverIndex).setLoad(infinity);

                                    }
                                    catch (IOException e) {
                                        Control.getInstance().removeServerCon(this);
                                        System.out.println("Incoming Connection with " + remoteHost + "  " + remotePort + " Crashed");
                                        int serverIndex = Control.getInstance().serverInList(remoteHost,remotePort);
                                        if(serverIndex >= 0)
                                            Control.getInstance().getServerAddresses().get(serverIndex).setLoad(infinity);
                                        destroyCon = true;
                                    }
                                    break;
                                } else {
                                    returnCommand.clear();
                                    returnCommand.put("command", "AUTHENTICATION_FAILED");
                                    returnCommand.put("info", "The username or secret is incorrect.");
                                    outd.write(returnCommand.toJSONString() + "\n");
                                    outd.flush();
                                    destroyCon = true;
                                    break;
                                }
                            } else {
                                returnCommand.clear();
                                returnCommand.put("command", "INVALID_MESSAGE");
                                returnCommand.put("info", "You have already authenticated. Now you will be kicked out.");
                                outd.write(returnCommand.toJSONString() + "\n");
                                outd.flush();
                                destroyCon = true;
                                break;
                            }
                        case "REGISTER":
                            username = command.get("username").toString();
                            secret = command.get("secret").toString();
                            //System.out.println(username);
                            boolean isHere = false;
                            for(int i = 0; i < Control.getInstance().getRegUsers().size(); i++)
                            {
                                if(Control.getInstance().getRegUsers().get(i).getUsername().equals(username))
                                {
                                    returnCommand.clear();
                                    returnCommand.put("command", "REGISTER_FAILED");
                                    returnCommand.put("info", "This username have already been registered");
                                    writeBroadcast(returnCommand.toString());
                                    isHere = true;
                                }
                            }
                            if(isHere)
                                break;
                            lc = new LockCount(username,secret,Control.getInstance().getServerAddresses().size());
                            lc.setRegisterPass(true);
                            Control.getInstance().getRegUsers().add(lc);
                            this.authenticatedUser = true;
                            returnCommand.clear();
                            returnCommand.put("command","REGISTER_SUCCESS");
                            returnCommand.put("username",username);
                            returnCommand.put("secret",secret);
                            flood(Control.getInstance().getServerCons(), returnCommand.toString(), true);
                            writeBroadcast(returnCommand.toString());
                            if(authenticatedUser)
                            {
                                sa = tryRedirect();
                                if(sa!=null)
                                    sendRedirect(sa);
                            }
                            break;
                        case "LOGIN":
                            boolean loginSuccess = false;
                            username = command.get("username").toString();
                            if (!username.equals("anonymous")) {
                                secret = command.get("secret").toString();
                            }
                            try {
                                if (!username.equals("anonymous")) {
                                    int index = Control.getInstance().userInList(username);
                                    if (index >= 0 && Control.getInstance().getRegUsers().get(index).isRegisterPass() &&
                                            Control.getInstance().getRegUsers().get(index).getSecret().equals(secret)) {
                                        this.authenticatedUser = true;
                                        returnCommand.clear();
                                        returnCommand.put("command", "LOGIN_SUCCESS");
                                        returnCommand.put("info", "logged in as user " + username + ".");
                                        loginSuccess = true;
                                        writeBroadcast(returnCommand.toJSONString());
                                    } else {
                                        returnCommand.clear();
                                        returnCommand.put("command", "LOGIN_FAILED");
                                        returnCommand.put("info", "Wrong username or password.");
                                        writeBroadcast(returnCommand.toJSONString());
                                        Control.getInstance().connectionClosed(this);
                                    }
                                } else {
                                    this.authenticatedUser = true;
                                    returnCommand.clear();
                                    returnCommand.put("command", "LOGIN_SUCCESS");
                                    loginSuccess = true;
                                    returnCommand.put("info", "logged in as user " + username + ".");
                                    outd.write(returnCommand.toJSONString() + "\n");
                                    outd.flush();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (loginSuccess) {
                                sa = tryRedirect();
                                if (sa != null) {
                                    sendRedirect(sa);
                                }
                            }
                            break;
                        case "ACTIVITY_MESSAGE":
                            if (this.authenticatedUser == false) {
                                returnCommand.clear();
                                returnCommand.put("command", "AUTHENTICATION_FAILED");
                                returnCommand.put("info", "Please login first.");
                                outd.write(returnCommand.toJSONString() + "\n");
                                outd.flush();
                            }

                            JSONObject returnMsg = (JSONObject) command.get("activity");
                            returnMsg.put("authenticated_user", username);
                            returnCommand.clear();
                            returnCommand.put("activity", returnMsg);
                            returnCommand.put("command", "ACTIVITY_BROADCAST");
                            System.out.println(Thread.currentThread().getName()
                                    + " - Message from client " + this.id + " received: " + data);
                            flood(Control.getInstance().getConnections(), returnCommand.toString(), true);


                            returnCommand.put("messageId",Integer.toString(messageId));
                            System.out.println("messageId "+messageId);
                            messageId++;
                            String clientId=socket.getRemoteSocketAddress().toString();
                            System.out.println("clientId "+ clientId);

                            returnCommand.put("clientId",clientId);
                            AckCount ac = new AckCount(returnCommand,0);
                            if(!Control.getInstance().getMessages().containsKey(clientId)){
                                ArrayList<AckCount> acList = new ArrayList<>();
                                acList.add(ac);
                                Control.getInstance().getMessages().put(clientId, acList);
                            }
                            else
                                Control.getInstance().getMessages().get(clientId).add(ac);
                            flood(Control.getInstance().getServerCons(), returnCommand.toString(), true);
                            System.out.println("Msg to Broad flooded");


                            //Control.getInstance().getMessages().get(clientId).add(ac);
                            //System.out.println("AckCount Added "+ac.getMessageId()+ "        " +ac.getMessage() );
                            //returnCommand.put("sender",socket.getInetAddress().getHostAddress());
                            //The Time out mechanism
                            /*
                            int countTimes=0;
                            try{
                                System.out.println("Waiting for acknowledgements");
                                Thread.sleep(1000);
                                ArrayList<AckCount> messages = Control.getInstance().getMessages().get(clientId);

                                for(int i = 0; i < messages.size(); i++) {
                                    if (messageId == messages.get(i).getMessageId()) {
                                        if (messages.get(i).getAckCount()!= Control.getInstance().getServerCons().size()) {
                                            for(countTimes=0;countTimes<3;countTimes++) {
                                                Thread.sleep(1000);
                                                if (messages.get(i).getAckCount() != Control.getInstance().getServerCons().size()) {
                                                    flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                                                }
                                                else
                                                    break;
                                            }
                                        }
                                    }
                                }
                                if(countTimes==3){
                                    System.out.print(" not receive enough ack number, 3 times  ");
                                }
                            } catch (InterruptedException e){
                                e.printStackTrace();
                            }
                            */
                            break;
                        case "INVALID_MESSAGE":
                            Control.getInstance().connectionClosed(this);
                            break;
                        case "AUTHENTICATION_FAIL":
                            Control.getInstance().connectionClosed(this);
                            break;
                        default:
                            Control.getInstance().connectionClosed(this);
                            break;
                    }
                    data = ind.readLine();
                }
            } else {
                command = new JSONObject();
                command.put("command", "AUTHENTICATE");
                command.put("secret", Settings.getSecret());
                command.put("hostname",InetAddress.getLocalHost().getHostAddress());
                command.put("port",Control.getInstance().getListener().getPortnum());
                this.writeBroadcast(command.toJSONString());
                if(!Control.getInstance().getStart())
                {
                    command.clear();
                    command.put("command","GET_TOPO");
                    this.writeBroadcast(command.toJSONString());
                    command.clear();
                    command.put("command","GET_REGUSERS");
                    this.writeBroadcast(command.toJSONString());
                    Control.getInstance().setStart(true);
                }
                try {
                    receiveLoad();
                } catch (SocketTimeoutException ste) {
                    System.out.println("Too long without a message from server: "
                            + remoteHost + "  " + remotePort);
                    Control.getInstance().removeServerCon(this);
                    socket.close();
                    reconnect();
                }
                catch (IOException e) {
                    Control.getInstance().removeServerCon(this);
                    System.out.println("Outgoing Connection to " + remoteHost + "  " + remotePort + " Crashed");
                    int serverIndex = Control.getInstance().serverInList(remoteHost,remotePort);
                    if(serverIndex >= 0)
                        Control.getInstance().getServerAddresses().get(serverIndex).setLoad(infinity);
                    socket.close();
                    reconnect();
                }

            }
        } catch (org.json.simple.parser.ParseException e) {
            System.out.println("Parse ERROR");
            try {
                outd.write("{ \"command\" : \"INVALID_MESSAGE\", \"info\" : \"You must send a valid JSON Object.\" }" + "\n");
                outd.flush();
            } catch (IOException d1) {
                System.out.println("Output ERROR");
            }
        }
        catch (NullPointerException e2) {
            System.out.println("Client unexpectedly gets offline without logout");
            e2.printStackTrace();
        } catch (IOException e)
        {
            Control.getInstance().getConnections().remove(this);
            System.out.println("IO EXCEPTION CONNECTION CLOSED");
        }
    }

    public void reconnect()
    {
        boolean stopTrying = false;
        int serverIndex = Control.getInstance().serverInList(remoteHost,remotePort);
        while (!stopTrying) {
            try {
                System.out.println("Trying to reconnect to " + remoteHost + "  " + remotePort);
                Control.getInstance().outgoingConnection(new Socket(remoteHost, remotePort));
                stopTrying = true;
            }catch (ConnectException ce)
            {
                System.out.println("Cannot reconnect to server " + remoteHost
                        + "  " + remotePort + " , try again after " + Settings.getTimeOut() + " milliseconds");
            }catch (IOException e)
            {
                System.out.println("Fatal error");
                Control.getInstance().removeServerCon(this);
                if(serverIndex >= 0)
                    Control.getInstance().getServerAddresses().get(serverIndex).setLoad(infinity);
            }
            try {
                Thread.sleep(Settings.getTimeOut());
            }catch (InterruptedException ie)
            {
                System.out.println("Trouble Sleeping when trying to reconnect");
            }
        }
    }

    public void sendRedirect(ServerAddress sa) throws IOException {
        JSONObject returnCommand = new JSONObject();
        returnCommand.clear();
        returnCommand.put("command", "REDIRECT");
        returnCommand.put("hostname", sa.getIpAddress());
        returnCommand.put("port", Integer.toString(sa.getPort()));
        writeBroadcast(returnCommand.toJSONString());
        closeCon();
    }


    public void receiveLoad() throws IOException, org.json.simple.parser.ParseException {
        int linkCount = 0;
        /*
        try {
            Thread.sleep(rand.nextInt(1000));
        }catch (InterruptedException ie)
        {
            System.out.println("Trouble Random Sleeping");
        }
        */
        while (true) {
            String str = ind.readLine();
            JSONObject command = (JSONObject) new JSONParser().parse(str);
            ServerAddress sa;
            JSONObject returnCommand = new JSONObject();
            String username;
            String secret;
            String announceID;
            String ipAddress;
            ArrayList<JSONObject> missedMsgIds = new ArrayList<>();
            int port;
            LockCount lc;
            String clientId;
            String messageId;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            switch (command.get("command").toString()) {
                //TODO
                case "GET_TOPO":
                    for(int i = 0; i < Control.getInstance().getServerAddresses().size();i++){
                        if(Control.getInstance().getServerAddresses().get(i).getPort() != remotePort ||
                                !Control.getInstance().getServerAddresses().get(i).getIpAddress().equals(remoteHost)){
                            //System.out.println(socket.getInetAddress().getHostAddress()); //127.0.0.1
                            returnCommand.clear();
                            returnCommand.put("command", "TOPO");
                            returnCommand.put("port", Integer.toString(Control.getInstance().getServerAddresses().get(i).getPort()));
                            returnCommand.put("hostname", Control.getInstance().getServerAddresses().get(i).getIpAddress());
                            outd.write(returnCommand.toJSONString() + "\n");
                            outd.flush();
                        }
                    }
                    break;
                case "GET_REGUSERS":
                    for(int i = 0; i < Control.getInstance().getRegUsers().size(); i++)
                    {
                        returnCommand.clear();
                        returnCommand.put("command","RegUser");
                        returnCommand.put("username",Control.getInstance().getRegUsers().get(i).getUsername());
                        returnCommand.put("secret",Control.getInstance().getRegUsers().get(i).getSecret());
                        outd.write(returnCommand.toJSONString() + "\n");
                        outd.flush();
                    }
                    break;
                case "TOPO":
                    //System.out.println(command.toString());
                        //linkCount++;
                        ipAddress = command.get("hostname").toString();
                        port = Integer.parseInt(command.get("port").toString());
                        if (ipAddress.equals(InetAddress.getLocalHost().getHostAddress())
                                && port == Control.getInstance().getListener().getPortnum())
                            break;
                        if (Control.getInstance().serverInList(ipAddress, port) < 0) {
                            //if(port!=Control.getInstance().getListener().getPortnum()) {
                            try {
                                Socket outSocket = new Socket(command.get("hostname").toString(), Integer.parseInt(command.get("port").toString()));
                                outSocket.setSoTimeout(Settings.getTimeOut());
                                Control.getInstance().outgoingConnection(outSocket);
                            } catch (ConnectException ce) {
                                System.out.println("Cannot connect to " + ipAddress + "  " + port);
                                int serverIndex = Control.getInstance().serverInList(ipAddress,port);
                                if(serverIndex >= 0)
                                    Control.getInstance().getServerAddresses().get(serverIndex).setLoad(infinity);
                                //System.out.println("ServerAddress NUM " + Control.getInstance().getServerAddresses().size());
                            } catch (IOException ie) {
                                System.out.println("Trouble Connecting");
                            }

                            //System.out.println("Received One Server Information");
                            // }
                        }

                    break;
                case "RegUser":
                    username = command.get("username").toString();
                    secret = command.get("secret").toString();
                    if(Control.getInstance().userInList(username) < 0) {
                        lc = new LockCount(username, secret, Control.getInstance().getServerAddresses().size());
                        lc.setRegisterPass(true);
                        Control.getInstance().getRegUsers().add(lc);
                        System.out.println("Add one RegUser");
                    }
                    break;
                case "ACTIVITY_BROADCAST":
                    //TODO : check if the message has already been broadcast or not
                    clientId = command.get("clientId").toString();
                    messageId = command.get("messageId").toString();
                    System.out.println("Message ID Here: " + messageId);
                    AckCount ac = new AckCount(command,Control.getInstance().getServerCons().size());
                    if(Control.getInstance().isSent(clientId,messageId)) {
                        System.out.println("Already Sent, Break");
                        break;
                    }
                    if(!Control.getInstance().getMessages().containsKey(clientId)){
                        ArrayList<AckCount> acList = new ArrayList<>();
                        acList.add(ac);
                        Control.getInstance().getMessages().put(clientId, acList);

                    }
                    flood(Control.getInstance().getServerCons(),command.toString(),false);
                    Control.getInstance().getMessages().get(clientId).add(ac);
                    command.remove("clientId");
                    command.remove("messageId");
                    flood(Control.getInstance().getConnections(),command.toString(),true);
                    break;

                    //We tried many times but the ack still does not work
                    /*
                    System.out.println(command);
                    returnCommand.clear();
                    returnCommand.put("command", "ACKNOWLEDGEMENT");
                    returnCommand.put("clientId", command.get("clientId"));
                    returnCommand.put("messageId", command.get("messageId"));
                    System.out.println("messageId: " + command.get("messageId"));
                    returnCommand.put("sender", socket.getInetAddress().getHostAddress());
                    System.out.println("the local addresses"+": "+socket.getInetAddress().getHostAddress());
                    returnCommand.put("receiver",remoteHost+" "+remotePort);
                    flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);


                    returnCommand.clear();
                    returnCommand.put("authenticated_user", command.get("authenticated_user"));
                    returnCommand.put("activity", command.get("activity"));
                    returnCommand.put("command", "ACTIVITY_BROADCAST");


                    ArrayList<AckCount> savedMessage = Control.getInstance().getMessages().get(command.get("clientId").toString());
                    int maxId = -1;
                    if (savedMessage == null){
                        AckCount ac = new AckCount(command,0);
                        ArrayList<AckCount> acList = new ArrayList<>();
                        acList.add(ac);
                        Control.getInstance().getMessages().put(command.get("clientId").toString(), acList);
                        System.out.println("AckCount Added (previous no this client id)"+ac);

                    }

                    savedMessage = Control.getInstance().getMessages().get(command.get("clientId").toString());


                    for (int j = 0; j < savedMessage.size(); j++) {
                        System.out.println("Get message id" +savedMessage.get(j).getMessage());
                        if (savedMessage.get(j).getMessageId() > maxId) {
                            maxId = savedMessage.get(j).getMessageId();
                        }
                    }
                    boolean isHere = false;

                    for (int i = 0; i < savedMessage.size(); i++) {
                        if (savedMessage.get(i).getClientId().equals(command.get("clientId").toString())
                                && (savedMessage.get(i).getMessageId()==Integer.parseInt(command.get("messageId").toString()))) {
                            isHere = true;
                            break;
                        }
                    }
                    if (!isHere) {
                        returnCommand.put("clientId", command.get("clientId"));
//                            returnCommand.put("sender", socket.getLocalAddress());
//                            returnCommand.put("receiver", remoteHost + " " + remotePort);
                        returnCommand.put("messageId", command.get("messageId"));
                        AckCount ac = new AckCount(returnCommand, 0);
                        Control.getInstance().getMessages().get(command.get("clientId")).add(ac);
                    }
                    //System.out.println(savedMessage.get(1).getMessage());
                    int currMsgId = Integer.parseInt(command.get("messageId").toString());
                    //if(currMsgId-maxId<2 && missedMsgIds.isEmpty()){
//                        flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), false);
//                        returnCommand.remove("clientId");
//                        returnCommand.remove("messageId");
//                        flood(Control.getInstance().getConnections(),returnCommand.toJSONString(),false);
//                        returnCommand.clear();
//                        returnCommand.put("command", "ACKNOWLEDGEMENT");
//                        returnCommand.put("clientId", command.get("clientId"));
//                        returnCommand.put("messageId", command.get("messageId"));
//                        returnCommand.put("sender", socket.getInetAddress().getHostAddress());
//                        System.out.println(socket.getInetAddress().getHostAddress());
//                        returnCommand.put("receiver",remoteHost+" "+remotePort);
//                        flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                    //} else {
                    while(currMsgId - maxId >= 2){
                        JSONObject missedMsgId = new JSONObject();
                        missedMsgId.put("clientId", command.get("clientId"));
                        missedMsgId.put("messageId", currMsgId-1);
                        missedMsgIds.add(missedMsgId);
                        currMsgId--;
                    }
                    //}
                    if(!missedMsgIds.isEmpty()){
                        if(currMsgId == Integer.parseInt(missedMsgIds.get(0).get("messageId").toString())
                                && command.get("clientId").toString().equals(missedMsgIds.get(0).get("clientId").toString())){
                            missedMsgIds.remove(0);
                            //after removing, judge the missed table
                            if(!missedMsgIds.isEmpty()){
                                for(int j = currMsgId; j < Integer.parseInt(missedMsgIds.get(0).get("messageId").toString()); j++){
                                    //ArrayList<AckCount> savedMsg=Control.getInstance().getMessages().get(command.get("clientId").toString());
                                    for(int k=0;k<savedMessage.size();k++){
                                        returnCommand=savedMessage.get(k).getMessage();
                                        if(savedMessage.get(k).getMessageId()==j) {
                                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), false);
                                            //returnCommand.remove("clientId");
                                            //returnCommand.remove("messageId");
                                            flood(Control.getInstance().getConnections(), returnCommand.toJSONString(), false);
                                        }
                                    }
                                }
                            }
                            else{
                                for(int j = currMsgId; j <= maxId; j++){
                                    //ArrayList<AckCount> savedMsg=Control.getInstance().getMessages().get(command.get("clientId").toString());
                                    for(int k=0;k<savedMessage.size();k++){
                                        returnCommand=savedMessage.get(k).getMessage();
                                        if(savedMessage.get(k).getMessageId()==j) {
                                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), false);
                                            //returnCommand.remove("clientId");
                                            //returnCommand.remove("messageId");
                                            flood(Control.getInstance().getConnections(), returnCommand.toJSONString(), false);
                                        }
                                    }
                                }


                            }
                        }else{
                            for (int k=0;k<missedMsgIds.size();k++){
                                if(currMsgId == Integer.parseInt(missedMsgIds.get(k).get("messageId").toString())
                                        && command.get("clientId").toString().equals(missedMsgIds.get(k).get("clientId").toString())){
                                    missedMsgIds.remove(k);
                                    break;
                                }
                            }
                        }
                    }
                    else{
                        returnCommand.clear();
                        returnCommand=command;
                        flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), false);
                        //returnCommand.remove("clientId");
                        //returnCommand.remove("messageId");
                        flood(Control.getInstance().getConnections(), returnCommand.toJSONString(), true);
                    }
                    */
                    //break;
                case "SERVER_ANNOUNCE":
                    //System.out.println(command);
                    ipAddress = command.get("hostname").toString();
                    port = Integer.parseInt(command.get("port").toString());
                    announceID = command.get("AnnounceID").toString();
                    int load = Integer.parseInt(command.get("load").toString());
                    int serverIndex = Control.getInstance().serverInList(ipAddress, port);
                    Date timeGot;
                    Date timeOriginal;
                    if (serverIndex >= 0) {
                        try {
                            timeGot = sdf.parse(announceID);
                            timeOriginal = sdf.parse(Control.getInstance().getServerAddresses().get(serverIndex).getAnnounceID());
                            if(timeOriginal.compareTo(timeGot) < 0)
                            {
                                System.out.println(command);
                                Control.getInstance().getServerAddresses().get(serverIndex).setLoad(load);
                                Control.getInstance().getServerAddresses().get(serverIndex).setAnnounceID(announceID);
                                try {
                                    Thread.sleep(100);
                                    flood(Control.getInstance().getServerCons(), str, false);
                                }catch (InterruptedException ie)
                                {
                                    System.out.println("Trouble waiting");
                                }
                            }
                        }
                        catch (java.text.ParseException p)
                        {
                            System.out.println("DateTime Parse ERROR");
                        }
                    }
                    break;
                    /*
                case "ACKNOWLEDGEMENT":
                    if(command.get("receiver").toString().equals(socket.getInetAddress().getHostAddress())){
                        clientId=command.get("clientId").toString();
                        messageId = command.get("messageId").toString();
                        String ackIndex=command.get("sender").toString()+" "+clientId+
                                " "+messageId;
                        if(!Control.getInstance().getSenderAckFlag().containsKey(ackIndex)){
                            Control.getInstance().getSenderAckFlag().put(ackIndex,true);
                            for(int p=0;p<Control.getInstance().getMessages().get(clientId).size();p++){
                                if(Control.getInstance().getMessages().get(clientId).get(p).getMessageId()==Integer.parseInt(messageId)){
                                    Control.getInstance().getMessages().get(clientId).get(p).addAckCount();
                                    break;
                                }
                            }

                        }
                    }else
                        flood(Control.getInstance().getServerCons(),str,false);
                    break;
                    */
                case "REGISTER_SUCCESS":
                    username = command.get("username").toString();
                    secret = command.get("secret").toString();
                    if(Control.getInstance().userInList(username) < 0) {
                        lc = new LockCount(username, secret, Control.getInstance().getServerAddresses().size());
                        lc.setRegisterPass(true);
                        Control.getInstance().getRegUsers().add(lc);
                        flood(Control.getInstance().getServerCons(), str, false);// In case of some servers are disconnected
                    }
                    //writeBroadcast(str);
                    break;
                //Previous code here
                default:
                    returnCommand.clear();
                    returnCommand.put("command", "INVALID_MESSAGE");
                    returnCommand.put("info", "You are not authenticated. Now you will be kicked out.");
                    outd.write(returnCommand.toJSONString() + "\n");
                    outd.flush();
                    Control.getInstance().connectionClosed(this);
                    socket.close();
                    break;
            }

        }
    }

    public void setRemoteHost(String remoteHost)
    {
        this.remoteHost = remoteHost;
    }
    public void setRemotePort(int port)
    {
        this.remotePort = port;
    }

    public synchronized void writeBroadcast(String clientMsg) throws IOException {
        outd.write(clientMsg + "\n");
        outd.flush();
    }

    public synchronized void flood(ArrayList<Connection> c, String msg, boolean all) throws IOException {
        if (all) {
            for (int i = 0; i < c.size(); i++) {

                c.get(i).writeBroadcast(msg);
            }
        } else {
            for (int i = 0; i < c.size(); i++) {
                if (c.get(i).getID() != this.id)
                    c.get(i).writeBroadcast(msg);
            }
        }

    }


    public Socket getSocket() {
        return socket;
    }

    public boolean isOpen() {
        return open;
    }
}
