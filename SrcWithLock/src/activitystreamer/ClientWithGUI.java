package activitystreamer;

import activitystreamer.client.ReceiveMessage;
import activitystreamer.client.TextFrame;
import activitystreamer.util.Settings;
import org.apache.commons.cli.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class ClientWithGUI {
    public static BufferedReader in = null;
    public static BufferedWriter out = null;
    public static Socket socket = null;
    private static TextFrame textFrame;
    private static JSONObject command = new JSONObject();

    public static TextFrame getTextFrame() {
        return textFrame;
    }

    public static void logIn() throws IOException {
        System.out.println("start login");
        command.clear();
        command.put("command", "LOGIN");
        command.put("username", Settings.getUsername());
        command.put("secret", Settings.getClientSecret());
        out.write(command.toJSONString() + "\n");
        out.flush();
        System.out.println("finish login");
    }

    public static void logOut() {
        try {
            JSONObject command = new JSONObject();
            command.clear();
            command.put("command", "LOGOUT");
            out.write(command.toJSONString() + "\n");
            out.flush();
            System.out.println("Disconnected");
            socket.close();
            System.exit(0);
        } catch (IOException e) {
            System.out.println("Connection had been previously closed. Client will now quit.");
            System.exit(0);
        }
    }

    private static void redirect(JSONObject command) {
        command.put("command", "REDIRECT");
    }

    public static void sendActivityObject(JSONObject msg) throws IOException {
        JSONObject command = new JSONObject();
        command.clear();
        command.put("command", "ACTIVITY_MESSAGE");
        command.put("username", Settings.getUsername());
        command.put("secret", Settings.getClientSecret());
        command.put("activity", msg);
        out.write(command.toJSONString() + "\n");
        out.flush();
        System.out.println("Text Frame Message sent");
    }

    private static void help(Options options) {
        String header = "An ActivityStream Client for Unimelb COMP90015\n\n";
        String footer = "\nGroup: Diablo IV";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ActivityStreamer.Client", header, options, footer, true);
        System.exit(-1);
    }


    public static void establishSocket(Socket currentSocket, String newJsonSocketString) {

        System.out.println("Connection established");
        // Get the input/output streams for reading/writing data from/to the socket
        try {
            currentSocket.close();
            JSONObject newJsonSocket = (JSONObject) (new JSONParser().parse(newJsonSocketString));
            String newHost = newJsonSocket.get("hostname").toString();
            int newPort = Integer.parseInt(newJsonSocket.get("port").toString());
            Settings.setRemoteHostname(newHost);
            Settings.setRemotePort(newPort);
            Socket newSocket = new Socket(newHost, newPort);
            in = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), "UTF-8"));
            ReceiveMessage rm = new ReceiveMessage(newSocket);
            rm.start();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (org.json.simple.parser.ParseException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("u", true, "username, default anonymous");
        options.addOption("rp", true, "remote port number, default 4500");
        options.addOption("rh", true, "remote hostname, default localhost");
        options.addOption("s", true, "secret for username, default test10 when register");

        // build the parser
        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e1) {
            help(options);
        }

        if (cmd.hasOption("rh")) {
            Settings.setRemoteHostname(cmd.getOptionValue("rh"));
        }

        if (cmd.hasOption("rp")) {
            try {
                int port = Integer.parseInt(cmd.getOptionValue("rp"));
                Settings.setClientRemotePort(port);
            } catch (NumberFormatException e) {
                help(options);
            }
        }

        if (cmd.hasOption("s")) {
            Settings.setClientSecret(cmd.getOptionValue("s"));
        }

        if (cmd.hasOption("u")) {
            Settings.setUsername(cmd.getOptionValue("u"));
        }

        try {
            // Create a stream socket bounded to any port and connect it to the
            // socket bound to localhost on port 4444
            socket = new Socket(Settings.getRemoteHostname(), Settings.getClientRemotePort());
            System.out.println("Connection established");

            // Get the input/output streams for reading/writing data from/to the socket
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            ReceiveMessage rm = new ReceiveMessage(socket);
            rm.start();

            Scanner scanner = new Scanner(System.in);
            String inputStr = null;

            if (Settings.getUsername().equals("anonymous")) {
                command.clear();
                command.put("command", "LOGIN");
                command.put("username", Settings.getUsername());
                System.out.println(command.toJSONString());
                out.write(command.toJSONString() + "\n");
                out.flush();
            } else if (!(Settings.getClientSecret() == null)) {
                command.clear();
                command.put("command", "LOGIN");
                command.put("username", Settings.getUsername());
                command.put("secret", Settings.getClientSecret());
                out.write(command.toJSONString() + "\n");
                out.flush();
            } else {
                command.clear();
                command.put("command", "REGISTER");
                command.put("username", Settings.getUsername());
                Settings.setClientSecret("test10");
                command.put("secret", "test10");
                out.write(command.toJSONString() + "\n");
                out.flush();
            }

            while (!(inputStr = scanner.next()).equals("quit")) {
                switch (inputStr.toLowerCase()) {
                    case "redirect":
                        redirect(command);
                        out.write(command.toJSONString() + "\n");
                        scanner.nextLine();
                        break;
                    default:
                        out.write(inputStr + "\n");
                        break;
                }
                out.flush();
                System.out.println("Command line Message sent");
            }
            out.write("quit");
            out.flush();

            scanner.close();
            socket.close();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the socket
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
