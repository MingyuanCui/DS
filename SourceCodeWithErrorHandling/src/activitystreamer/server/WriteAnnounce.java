package activitystreamer.server;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;

import java.net.UnknownHostException;
import java.util.Date;
import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import java.io.IOException;

public class WriteAnnounce extends Thread{
    private int index;
    String announce;
    WriteAnnounce(int index, String announce)
    {
        this.index = index;
        this.announce = announce;
        start();
    }
    WriteAnnounce(int index)
    {
        this.index = index;
    }

    ///*
    public void run() {
        try {
            Control.getInstance().getServerCons().get(index).writeBroadcast(announce);
        }catch (IOException e)
        {
            System.out.println("Cannot send server announce to server " + index);
        }

    }
    //*/
    /*
    @Override
    public void run() {
        JSONObject returnCommand = new JSONObject();
        while (true)
        {
            returnCommand.clear();
            returnCommand.put("command", "SERVER_ANNOUNCE");
            returnCommand.put("port", Control.getInstance().getListener().getPortnum());
            returnCommand.put("load", Control.getInstance().getConnections().size());
            try {
                returnCommand.put("hostname", InetAddress.getLocalHost().getHostAddress());
            }catch (UnknownHostException ue)
            {
                System.out.println("Host Not Found");
            }
            Date date = new Date();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            returnCommand.put("AnnounceID", df.format(date));
            try {
                Control.getInstance().getServerCons().get(index).writeBroadcast(returnCommand.toJSONString());
            }catch (IOException e)
            {
                System.out.println("Write Announce ERROR");
            }
            try {
                Thread.sleep(Settings.getActivityInterval());
            }catch (InterruptedException ie)
            {
                System.out.println("Trouble sleeping while doing server announce");
            }
        }
    }
    */
}
