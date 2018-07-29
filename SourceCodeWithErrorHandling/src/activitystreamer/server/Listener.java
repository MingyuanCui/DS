package activitystreamer.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Listener extends Thread {
    private ServerSocket serverSocket = null;
    private boolean term = false;
    private int portnum;

    public Listener(int portnum) throws IOException {
        this.portnum = portnum;
        //File file = new File(portnum + ".txt");
        //createNewFile(file);
        serverSocket = new ServerSocket(portnum);
        start();
    }

    public static void createNewFile(File file) {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int getPortnum() {
        return portnum;
    }

    @Override
    public void run() {

        while (true) {
            Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();
                Control.getInstance().incomingConnection(clientSocket);
                //System.out.println("LocalPort: " + clientSocket.getLocalPort());
            } catch (IOException e) {

            }
        }
    }

    public void setTerm(boolean term) {
        this.term = term;
        if (term) interrupt();
    }

    public ServerSocket getServerSocket() {
        return this.serverSocket;
    }

}
