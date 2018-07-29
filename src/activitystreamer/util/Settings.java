package activitystreamer.util;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;

public class Settings {
    private static SecureRandom random = new SecureRandom();
    private static int localPort = 4500;
    private static String localHostname = "localhost";
    private static String remoteHostname = "localhost";
    private static int remotePort = -1;
    private static int clientRemotePort = 4500;
    private static int activityInterval = 5000; // milliseconds
    private static String secret = "fmnmpp3ai91qb3gc2bvs14g3ue";
    private static String clientSecret = null;
    private static String username = "anonymous";

    public static void setSecret(String s) {
        secret = s;
    }

    public static void setLocalPort(int lp) {
        localPort = lp;
    }

    public static String getClientSecret() {
        return clientSecret;
    }

    public static void setClientSecret(String ds) {
        clientSecret = ds;
    }

    public static void setClientRemotePort(int rp) {
        clientRemotePort = rp;
    }

    public static int getClientRemotePort() {
        return clientRemotePort;
    }
    public static ServerSocket isPortUsed() {
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(getLocalPort());
        } catch (IOException e) {
            return null;
        }
        return serverSocket;
    }


    public static int getLocalPort() {

        return localPort;
    }

    public static int getRemotePort() {
        return remotePort;
    }

    public static void setRemotePort(int remotePort) {
        if (remotePort < 0 || remotePort > 65535) {
            //log.error("supplied port "+remotePort+" is out of range, using "+getRemotePort());
        } else {
            Settings.remotePort = remotePort;
        }
    }

    public static String getRemoteHostname() {
        return remoteHostname;
    }

    public static void setRemoteHostname(String remoteHostname) {
        Settings.remoteHostname = remoteHostname;
    }

    public static int getActivityInterval() {
        return activityInterval;
    }

    public static String getSecret() {
        return secret;
    }

    public static String getUsername() {
        return username;
    }

    public static String getLocalHostname() {
        return localHostname;
    }

    public static void setUsername(String username) {
        Settings.username = username;
    }

	/*
     * some general helper functions
	 */

    public static String socketAddress(Socket socket) {
        return socket.getInetAddress() + ":" + socket.getPort();
    }

    public static String nextSecret() {
        return new BigInteger(130, random).toString(32);
    }


}
