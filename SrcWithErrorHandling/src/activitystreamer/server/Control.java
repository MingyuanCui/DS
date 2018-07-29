package activitystreamer.server;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import java.util.HashMap;
import java.util.Scanner;

import org.json.simple.JSONObject;
import activitystreamer.util.Settings;

public class Control extends Thread {

    private static ArrayList<Connection> connections;
    private static ArrayList<Connection> serverCons;
    private static boolean term = false;
    private static Listener listener;
    private boolean isAuthenticated = false;
    protected static Control control = null;
    private static ArrayList<ServerAddress> serverAddresses;
    private static JSONObject returnCommand = new JSONObject();
    private static int count = 0;
    private static ArrayList<LockCount> regUsers;
    private static String serverID;
    private static int brokenCons = 0;
    private boolean start = false;
    private static HashMap<String,Boolean> senderAckFlag;
    private static HashMap<String,ArrayList<AckCount>> messages;
    private static ArrayList<AckCount> ackNumber;

    public synchronized static int getBrokenCons()
    {
        return brokenCons;
    }

    public synchronized static void addBrokenCons()
    {
        brokenCons++;
    }

    public synchronized boolean getStart()
    {
        return start;
    }

    public synchronized void setStart(boolean start)
    {
        this.start = start;
    }

    public synchronized static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    public Control() {
        // initialize the connections array
        senderAckFlag=new HashMap<>();
        messages = new HashMap<String, ArrayList<AckCount>>();
        connections = new ArrayList<Connection>();
        serverCons = new ArrayList<Connection>();
        serverAddresses = new ArrayList<ServerAddress>();
        regUsers = new ArrayList<LockCount>();
        // start a listener
        try {
            listener = new Listener(Settings.getLocalPort());
            ServerAddress sa = new ServerAddress(InetAddress.getLocalHost().getHostAddress(),listener.getPortnum(),0);
            serverAddresses.add(sa);
            serverID = InetAddress.getLocalHost().getHostAddress() + ":" + listener.getPortnum();
            System.out.println("Server is listening on port " + Settings.getLocalPort());
        } catch (IOException e1) {
            System.out.println("This local port " + Settings.getLocalPort() + " is occupied!");
            System.exit(-1);
        }
        start();
    }

    public void initiateConnection() {
        try {
            int remotePort = Settings.getRemotePort();
            outgoingConnection(new Socket(Settings.getRemoteHostname(), remotePort));
        } catch (IOException e) {
            System.out.println("Failed to make connection to " + Settings.getRemoteHostname() + ":"
                    + Settings.getRemotePort() + "\n" + e);
            System.exit(-1);
        }
    }

    /*
     * Processing incoming messages from the connection.
     * Return true if the connection should close.
     */
    public synchronized boolean process(Connection con, String msg) {
        return true;
    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con) {
        if (!term) connections.remove(con);
    }

    /*
     * A new incoming connection has been established, and a reference is returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {

        Connection c;
        c = new Connection(s, count++);
        connections.add(c);
        System.out.println("Add a new connection!");
        return c;
    }

    /*
     * A new outgoing connection has been established, and a reference is returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        //log.debug("outgoing connection: "+Settings.socketAddress(s));
        boolean isHere = false;
        if(s.getInetAddress().getHostAddress().equals("127.0.0.1"))
            Settings.setRemoteHostname(InetAddress.getLocalHost().getHostAddress());
        //System.out.println(Settings.getRemoteHostname() + "  " + s.getPort());
        for (int i = 0; i < serverAddresses.size();i++)
        {
            if(serverAddresses.get(i).getIpAddress().equals(Settings.getRemoteHostname()) &&
                    serverAddresses.get(i).getPort() == s.getPort())
                isHere = true;
        }
        if(Settings.getRemoteHostname().equals(InetAddress.getLocalHost().getHostAddress())
                && s.getPort() == listener.getPortnum())
            isHere = true;
        if(isHere)
        {
            //System.out.println("Here");
            return null;
        }

        Connection c;
        s.setSoTimeout(Settings.getTimeOut());
        c = new Connection(s, serverCons.size(),true);

        Control.getInstance().getServerCons().add(c);
        ServerAddress sa;
        if(s.getInetAddress().getHostAddress().equals("127.0.0.1"))
            sa = new ServerAddress(InetAddress.getLocalHost().getHostAddress(), s.getPort(), 0);
        else
            sa = new ServerAddress(s.getInetAddress().getHostAddress(), s.getPort(), 0);
        c.setRemoteHost(sa.getIpAddress());
        c.setRemotePort(sa.getPort());
        Control.getInstance().getServerAddresses().add(sa);
        System.out.println("Connecting to: " + sa.getIpAddress() + "  " + sa.getPort());

        return c;
    }

    public synchronized void removeServerAddr(String ipAddress, int port)
    {
        ArrayList<ServerAddress> sadd = Control.getInstance().getServerAddresses();
        for(int i = 0; i < sadd.size(); i++)
        {
            if(sadd.get(i).getIpAddress().equals(ipAddress) &&
                    sadd.get(i).getPort() == port)
                sadd.remove(i);
        }
    }

    public synchronized void removeServerCon(Connection serverCon)
    {
        Control.getInstance().getServerCons().remove(serverCon);
    }

    public static String getServerID()
    {
        return serverID;
    }

    @Override

    public void run() {
        /*
        for (int i = 0 ; i < serverCons.size(); i++)
        {
            WriteAnnounce wa = new WriteAnnounce(i);
        }
        */
        while (!term) {
            // do something with 5 second intervals in between
            try {
                //Thread.sleep(30000);
                ///*
                returnCommand.clear();
                returnCommand.put("command", "SERVER_ANNOUNCE");
                returnCommand.put("port", listener.getPortnum());
                returnCommand.put("load", connections.size());
                returnCommand.put("hostname", InetAddress.getLocalHost().getHostAddress());
                Date date = new Date();
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                returnCommand.put("AnnounceID", df.format(date));
                for (int i = 0; i < serverCons.size(); i++) {
                    WriteAnnounce wa = new WriteAnnounce(i,returnCommand.toJSONString());
                    //System.out.println("Writing Finished");
                }
                //*/


                serverAddresses.get(0).setAnnounceID(df.format(date));

                //for(int i = 0; i < regUsers.size(); i++)
                    //System.out.println(regUsers.get(i).getUsername() + "  " + regUsers.get(i).getSecret());
                Thread.sleep(Settings.getActivityInterval());
                //System.out.println("ServerAddressNum " + serverAddresses.size());
                //System.out.println("ServerNum " + serverCons.size());
            } catch (InterruptedException e) {
                break;
            }catch (IOException ie)
            {
                continue;
            }
            if (!term) {
                term = doActivity();
            }
        }

        // clean up
        /*
        System.out.println("Cleaning up");
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);
        */

    }
    public synchronized int userInList(String username) {
        for (int i = 0; i < Control.getInstance().getRegUsers().size(); i++) {
            if (Control.getInstance().getRegUsers().get(i).getUsername().equals(username))
                return i;
        }
        return -1;
    }

    public synchronized int serverInList(String ipAddress, int port) {
        for (int i = 0; i < Control.getInstance().getServerAddresses().size(); i++) {
            if (Control.getInstance().getServerAddresses().get(i).getIpAddress().equals(ipAddress) &&
                    Control.getInstance().getServerAddresses().get(i).getPort() == port) {
                return i;
            }
        }
        return -1;
    }

    public boolean doActivity() {
        return false;
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public synchronized final ArrayList<Connection> getConnections() {
        return connections;
    }

    public synchronized final ArrayList<Connection> getServerCons() {
        return serverCons;
    }

    public synchronized final ArrayList<ServerAddress> getServerAddresses() {
        return serverAddresses;
    }

    public synchronized final ArrayList<LockCount> getRegUsers() {
        return regUsers;
    }
    public synchronized final ArrayList<AckCount> getAckNumber(){return ackNumber;}

    public synchronized final HashMap<String,ArrayList<AckCount>> getMessages(){return messages;}

    public synchronized  final HashMap<String,Boolean> getSenderAckFlag(){return senderAckFlag;}


    public final Listener getListener() {
        return listener;
    }

    public boolean isSent(String clientId, String messageId)
    {
        if(messages.containsKey(clientId))
        {
            ArrayList<AckCount> ac = messages.get(clientId);
            for (int i = 0; i < ac.size(); i++)
            {
                //if(ac.get(i).getMessageId().equals(messageId))
                if(ac.get(i).getMessageId()!=null)
                    if(ac.get(i).getMessageId().equals(messageId))
                        return true;
            }
        }
        return false;
    }
}
