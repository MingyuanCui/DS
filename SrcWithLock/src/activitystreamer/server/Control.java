package activitystreamer.server;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

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

    public synchronized static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    public Control() {
        // initialize the connections array
        connections = new ArrayList<Connection>();
        serverCons = new ArrayList<Connection>();
        serverAddresses = new ArrayList<ServerAddress>();
        regUsers = new ArrayList<LockCount>();
        // start a listener
        try {
            listener = new Listener(Settings.getLocalPort());
            System.out.println("Server is listening on port " + Settings.getLocalPort());
        } catch (IOException e1) {
            System.out.println("This local port " + Settings.getLocalPort() + " is occupied!");
            System.exit(-1);
        }
        start();
    }

	/*public void initiateConnection(){
		// make a connection to another server if remote hostname is supplied
		if(Settings.getLocalPort()!=listener.getServerSocket().getLocalPort()){
			try {
				System.out.print("Please input a server port to connect a server: ");
				Scanner serverPort = new Scanner(System.in);
				int remotePort = Integer.parseInt(serverPort.nextLine());
				outgoingConnection(new Socket(Settings.getRemoteHostname(),remotePort));

			} catch (IOException e) {
				System.exit(-1);
			}
		}
	}*/

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
        Connection c;
        c = new Connection(s, serverCons.size());
        c.setFlag(true);
        serverCons.add(c);
        return c;

    }


    @Override

    public void run() {
        while (!term) {
            // do something with 5 second intervals in between
            try {
                returnCommand.clear();
                returnCommand.put("command", "SERVER_ANNOUNCE");
                returnCommand.put("port", listener.getPortnum());
                returnCommand.put("load", connections.size());
                returnCommand.put("hostname", InetAddress.getLocalHost().getHostAddress());
                returnCommand.put("id", listener.getPortnum());
                for (int i = 0; i < serverCons.size(); i++) {
                    serverCons.get(i).writeBroadcast(returnCommand.toJSONString());
                }
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                continue;
            }
            if (!term) {

                term = doActivity();
            }

        }

        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);

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


    public final Listener getListener() {
        return listener;
    }
}
