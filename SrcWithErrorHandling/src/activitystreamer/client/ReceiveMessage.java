package activitystreamer.client;

import activitystreamer.ClientWithGUI;
import activitystreamer.util.Settings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;


public class ReceiveMessage extends Thread {

    private BufferedReader reader;
    private Socket currentSocket;

    public ReceiveMessage(Socket currentSocket) throws IOException {
        this.reader = new BufferedReader(new InputStreamReader(currentSocket.getInputStream(), "UTF-8"));
        this.currentSocket = currentSocket;
    }


    @Override
    public void run() {
        try {
            String jsonMsg = null;
            TextFrame textFrame = new TextFrame();
            while ((jsonMsg = reader.readLine()) != null) {
                try {
                    JSONObject msg = (JSONObject) (new JSONParser().parse(jsonMsg));
                    switch (msg.get("command").toString()) {
                        case "REDIRECT":
                            ClientWithGUI.establishSocket(currentSocket, jsonMsg);
                            ClientWithGUI.logIn();
                            currentSocket.close();
                            System.out.println(jsonMsg);
                            textFrame.dispose();
                            break;
                        case "INVALID_MESSAGE":
                        case "AUTHENTICATION_FAIL":
                            ClientWithGUI.establishSocket(currentSocket, "{ \"hostname\" : " + "\"" + Settings.getRemoteHostname() + "\", \"port\" : " + "\"" + currentSocket.getPort() + "\" }" + "\n");
                            ClientWithGUI.logIn();
                            currentSocket.close();
                            textFrame.dispose();
                            break;
                        case "LOGIN_FAILED":
                        case "REGISTER_FAILED":
                            System.out.println(jsonMsg);
                            System.exit(0);
                        default:
                            break;
                    }
                    textFrame.setOutputText(msg);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                System.out.println("Received Message " + jsonMsg);
            }

        } catch (SocketException e) {

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}