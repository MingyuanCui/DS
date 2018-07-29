package activitystreamer.client;

import java.io.*;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class ClientSkeleton extends Thread {
    //private static final Logger log = LogManager.getLogger();
    private static ClientSkeleton clientSolution;
    private TextFrame textFrame;


    public static ClientSkeleton getInstance() {
        if (clientSolution == null) {
            clientSolution = new ClientSkeleton();
        }
        return clientSolution;
    }

    public ClientSkeleton() {

        textFrame = new TextFrame();
        start();
    }


    @SuppressWarnings("unchecked")
    private static void sendActivityObject(JSONObject msg) {


    }

    public void disconnect() {

    }


    public void run() {

    }


}
