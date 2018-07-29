package activitystreamer.server;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Connection extends Thread {

    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inreader;
    private PrintWriter outwriter;
    private boolean open = false;
    private Socket socket;
    private boolean flag = false;//True for server connection; False for server-client
    private boolean term = false;
    private boolean authenticatedUser = false;
    private boolean authenticatedServer = false;
    private int id;
    private String username = null;
    private String secret = null;
    BufferedReader ind;
    BufferedWriter outd;

    public Connection(Socket socket, int id) throws IOException {
        ind = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        outd = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        this.socket = socket;
        open = true;
        this.id = id;
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
    public boolean writeMsg(String msg) {
        if (open) {
            outwriter.println(msg);
            outwriter.flush();
            return true;
        }
        return false;
    }

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
        int minLoad = 100000;
        int newPort = 0;
        String newAddress = "";
        ArrayList<ServerAddress> sa = Control.getInstance().getServerAddresses();
        for (int i = 0; i < sa.size(); i++) {
            if (sa.get(i).getLoad() < minLoad) {
                minLoad = sa.get(i).getLoad();
                newAddress = sa.get(i).getIpAddress();
                newPort = sa.get(i).getPort();
            }
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
        FileWriter fileWriter;
        FileReader fileReader;
        BufferedReader txtReader;
        try {
            fileWriter = new FileWriter(Control.getInstance().getListener().getPortnum() + ".txt", true);
            fileWriter.write("\r\n");
            fileWriter.flush();
            fileReader = new FileReader(Control.getInstance().getListener().getPortnum() + ".txt");
            if (!this.flag) {
                data = ind.readLine();
                ServerAddress sa;
                command = (JSONObject) (new JSONParser().parse(data));
                while (!command.get("command").toString().equals("LOGOUT")) {
                    command = (JSONObject) (new JSONParser().parse(data));
                    System.out.println(command.get("command").toString());

                    switch (command.get("command").toString()) {
                        case "AUTHENTICATE":
                            if (!this.authenticatedServer) {
                                if (command.get("secret").toString().equals(Settings.getSecret())) {
                                    setFlag(true);
                                    Control.getInstance().getServerCons().add(this);
                                    this.setID(Control.getInstance().getServerCons().size());
                                    Control.getInstance().getConnections().remove(this);
                                    this.authenticatedServer = true;
                                    try {
                                        receiveLoad();
                                    } catch (IOException e) {
                                        System.out.println("Server Broadcast Error");
                                    }
                                    break;
                                } else {
                                    returnCommand.clear();
                                    returnCommand.put("command", "AUTHENTICATION_FAILED");
                                    returnCommand.put("info", "The username or secret is incorrect.");
                                    outd.write(returnCommand.toJSONString() + "\n");
                                    outd.flush();
                                    break;
                                }
                            } else {
                                returnCommand.clear();
                                returnCommand.put("command", "INVALID_MESSAGE");
                                returnCommand.put("info", "You have already authenticated. Now you will be kicked out.");
                                outd.write(returnCommand.toJSONString() + "\n");
                                outd.flush();
                                break;
                            }

                        case "REGISTER":
                            username = command.get("username").toString();
                            secret = command.get("secret").toString();
                            String commands = username + " " + secret;
                            System.out.println(username);
                            boolean isHere = false;
                            try {
                                txtReader = new BufferedReader(fileReader);
                                String tempString = "";
                                while ((tempString = txtReader.readLine()) != null) {
                                    String[] loginInfo = tempString.split(" ");
                                    if (loginInfo[0].equals(username)) {
                                        isHere = true;

                                        outd.write("{ \"command\" : \"REGISTER_FAILED\", \"info\" : \"" + username + " is already registered with the system\" }" + "\n");
                                        outd.flush();
                                        break;
                                    }
                                }
                                if (isHere) {
                                    break;
                                } else {
                                    LockCount lc = new LockCount(username, secret, 0);
                                    Control.getInstance().getRegUsers().add(lc);
                                }
                                returnCommand.clear();
                                returnCommand.put("command", "LOCK_REQUEST");
                                returnCommand.put("username", username);
                                returnCommand.put("secret", secret);
                                flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                                try {
                                    System.out.println("Checking");
                                    ArrayList<LockCount> lc = Control.getInstance().getRegUsers();
                                    Thread.sleep(Settings.getActivityInterval());
                                    if (Control.getInstance().getServerAddresses().size() == 0) {
                                        fileWriter.write(commands + "\n");
                                        System.out.println(commands);
                                        fileWriter.flush();
                                        fileWriter.close();
                                        outd.write("{ \"command\" : \"REGISTER_SUCCESS\", \"info\" : \"register success for " + username + "\" }" + "\n");
                                        outd.flush();
                                        this.authenticatedUser = true;
                                        break;
                                    }
                                    for (int i = 0; i < lc.size(); i++) {
                                        System.out.println("Once checked" + Control.getInstance().getServerAddresses().size());
                                        if (lc.get(i).getLockcount() == Control.getInstance().getServerAddresses().size() &&
                                                !lc.get(i).isRegisterPass()) {
                                            outd.write("{ \"command\" : \"REGISTER_SUCCESS\", \"info\" : \"register success for " + username + "\" }" + "\n");
                                            outd.flush();
                                            returnCommand.clear();
                                            returnCommand.put("command", "REGISTER_SUCCESS");
                                            returnCommand.put("username", username);
                                            returnCommand.put("secret", secret);
                                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                                            System.out.println(returnCommand.toJSONString());
                                            lc.get(i).setRegisterPass(true);
                                            lc.get(i).setSecret(secret);
                                            try {
                                                fileWriter.write(commands + "\n");
                                                fileWriter.flush();
                                                fileWriter.close();
                                                this.authenticatedUser = true;
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                            break;
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    System.out.println("trouble sleeping");
                                }
                                fileReader.close();
                            } catch (IOException e) {

                            }
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
                                boolean loginSuccessFlag = false;
                                if (!username.equals("anonymous")) {
                                    fileReader = new FileReader(Control.getInstance().getListener().getPortnum() + ".txt");
                                    txtReader = new BufferedReader(fileReader);
                                    String tempString;
                                    while ((tempString = txtReader.readLine()) != null) {
                                        String[] loginInfo = tempString.split(" ");
                                        if (loginInfo[0].equals(username) && loginInfo[1].equals(secret)) {
                                            loginSuccessFlag = true;
                                            this.authenticatedUser = true;
                                            returnCommand.clear();
                                            returnCommand.put("command", "LOGIN_SUCCESS");
                                            returnCommand.put("info", "logged in as user " + username + ".");
                                            loginSuccess = true;
                                            outd.write(returnCommand.toJSONString() + "\n");
                                            outd.flush();
                                            break;

                                        } else if (loginInfo[0].equals(username) && !loginInfo[1].equals(secret)) {
                                            loginSuccessFlag = true;
                                            returnCommand.clear();
                                            returnCommand.put("command", "LOGIN_FAILED");
                                            returnCommand.put("info", "Username or secret incorrect.");
                                            outd.write(returnCommand.toJSONString() + "\n");
                                            outd.flush();
                                            break;
                                        }
                                    }
                                    if (!loginSuccessFlag) {
                                        int index = userInList(username);
                                        if (index >= 0 && Control.getInstance().getRegUsers().get(index).isRegisterPass() &&
                                                Control.getInstance().getRegUsers().get(index).getSecret().equals(secret)) {
                                            this.authenticatedUser = true;
                                            returnCommand.clear();
                                            returnCommand.put("command", "LOGIN_SUCCESS");
                                            returnCommand.put("info", "logged in as user " + username + ".");
                                            loginSuccess = true;
                                            writeBroadcast(returnCommand.toJSONString());
                                        } else {
                                            fileReader.close();
                                            returnCommand.clear();
                                            LockCount lc = new LockCount(username, secret, 0);
                                            Control.getInstance().getRegUsers().add(lc);
                                            returnCommand.put("command", "LOGIN_REQUEST");
                                            returnCommand.put("username", username);
                                            returnCommand.put("secret", secret);
                                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                                            try {
                                                Thread.sleep(Settings.getActivityInterval());
                                                if ((!lc.isRegisterPass()) && lc.getLockcount() == Control.getInstance().getServerAddresses().size()) {
                                                    returnCommand.clear();
                                                    returnCommand.put("command", "LOGIN_FAILED");
                                                    returnCommand.put("info", "No user named " + username + ".");
                                                    writeBroadcast(returnCommand.toJSONString());
                                                    Control.getInstance().connectionClosed(this);
                                                }

                                            } catch (InterruptedException e) {
                                                System.out.println("Trouble Sleeping");
                                            }
                                        }
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
                                returnCommand.put("command", "AUTHENTICATION_FAIL");
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
                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                            flood(Control.getInstance().getConnections(), returnCommand.toJSONString(), true);
                            break;
                        case "INVALID_MESSAGE":
                            Control.getInstance().connectionClosed(this);
                            break;
                        case "AUTHENTICATION_FAIL":
                            Control.getInstance().connectionClosed(this);
                            break;
                        default:
                            break;
                    }
                    data = ind.readLine();
                }
            } else {
                command = new JSONObject();
                command.put("command", "AUTHENTICATE");
                command.put("secret", Settings.getSecret());
                this.writeBroadcast(command.toJSONString());
                try {
                    receiveLoad();
                } catch (IOException e) {

                }
            }
        } catch (org.json.simple.parser.ParseException e) {
            System.out.println("Parse ERROR");
            try {
                outd.write("{ \"command\" : \"INVALID_MESSAGE\", \"info\" : \"You must send a valid JSON Object.\" }" + "\n");
                outd.flush();
            } catch (IOException d1) {

            }
        } catch (IOException e1) {

        } catch (NullPointerException e2) {

        } finally {
            Control.getInstance().connectionClosed(this);
            open = false;
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
        while (true) {
            String str = ind.readLine();
            JSONObject command = (JSONObject) new JSONParser().parse(str);
            ServerAddress sa;
            JSONObject returnCommand = new JSONObject();
            String username;
            String secret;
            FileReader fileReader;
            switch (command.get("command").toString()) {
                case "ACTIVITY_BROADCAST":
                    flood(Control.getInstance().getServerCons(), str, false);
                    flood(Control.getInstance().getConnections(), str, true);
                    break;
                case "SERVER_ANNOUNCE":
                    System.out.println(command);
                    String ipAddress = command.get("hostname").toString();
                    int port = Integer.parseInt(command.get("port").toString());
                    int load = Integer.parseInt(command.get("load").toString());
                    int serverIndex = serverInList(ipAddress, port);
                    if (serverIndex >= 0) {
                        ArrayList<ServerAddress> sadd = Control.getInstance().getServerAddresses();
                        sadd.get(serverIndex).setLoad(load);
                    } else {
                        sa = new ServerAddress(ipAddress, port, load);
                        Control.getInstance().getServerAddresses().add(sa);
                    }
                    flood(Control.getInstance().getServerCons(), str, false);
                    break;
                case "LOCK_ALLOWED":
                    username = command.get("username").toString();
                    secret = command.get("secret").toString();
                    System.out.println("Received Lock");
                    int index = userInList(username);
                    if (index >= 0) {
                        Control.getInstance().getRegUsers().get(index).addCount();
                        System.out.println(username + "  " + Control.getInstance().getRegUsers().get(index).getLockcount());
                    } else {
                        flood(Control.getInstance().getServerCons(), str, false);
                        System.out.println("Flood LockAllowed");
                    }
                    break;
                case "REGISTER_SUCCESS":
                    username = command.get("username").toString();
                    secret = command.get("secret").toString();
                    LockCount lc = new LockCount(username, secret, Control.getInstance().getServerAddresses().size());
                    lc.setRegisterPass(true);
                    Control.getInstance().getRegUsers().add(lc);
                    flood(Control.getInstance().getServerCons(), str, false);
                    break;
                case "LOCK_DENIED":
                    username = command.get("username").toString();
                    //TODO
                    boolean isDenied = false;
                    for (int i = 0; i < Control.getInstance().getConnections().size(); i++) {
                        if (Control.getInstance().getConnections().get(i).username.equals(username)) {
                            isDenied = true;
                            System.out.println("Here Denied");
                            Control.getInstance().getConnections().get(i).outd.write("{ \"command\" : \"REGISTER_FAILED\", \"info\" : \"" + username + " is already registered with the system\" }" + "\n");
                            Control.getInstance().getConnections().get(i).outd.flush();
                        }
                    }
                    if (!isDenied) {
                        System.out.println("Once flood");
                        flood(Control.getInstance().getServerCons(), str, false);
                    }
                    break;
                //TODO
                case "LOCK_REQUEST":
                    flood(Control.getInstance().getServerCons(), str, false);
                    username = command.get("username").toString();
                    secret = command.get("secret").toString();
                    boolean denied = false;
                    try {
                        fileReader = new FileReader(Control.getInstance().getListener().getPortnum() + ".txt");
                        BufferedReader txtReader = new BufferedReader(fileReader);
                        String tempString = "";
                        while ((tempString = txtReader.readLine()) != null) {
                            String[] loginInfo = tempString.split(" ");
                            if (loginInfo[0].equals(username)) {
                                returnCommand.clear();
                                returnCommand.put("command", "LOCK_DENIED");
                                returnCommand.put("username", username);
                                returnCommand.put("secret", secret);
                                System.out.println("Denied send");
                                flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                                denied = true;
                                break;
                            }
                        }
                    } catch (IOException e) {
                        System.out.println("Checking failed");
                    }
                    if (denied)
                        break;
                    else {
                        int hasUser = userInList(username);
                        if (hasUser >= 0) {
                            returnCommand.put("command", "LOCK_DENIED");
                            returnCommand.put("username", username);
                            returnCommand.put("secret", secret);
                            System.out.println("Denied send");
                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                            break;
                        } else {
                            returnCommand.clear();
                            returnCommand.put("command", "LOCK_ALLOWED");
                            returnCommand.put("username", username);
                            returnCommand.put("secret", secret);
                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                            System.out.println("Send LockAllowed ");
                            break;
                        }
                    }
                case "LOGIN_REQUEST":
                    flood(Control.getInstance().getServerCons(), str, false);
                    username = command.get("username").toString();
                    secret = command.get("secret").toString();
                    fileReader = new FileReader(Control.getInstance().getListener().getPortnum() + ".txt");
                    BufferedReader txtReader = new BufferedReader(fileReader);
                    String tempString = "";
                    boolean allowed = false;
                    while ((tempString = txtReader.readLine()) != null) {
                        String[] loginInfo = tempString.split(" ");
                        if (loginInfo[0].equals(username) && loginInfo[1].equals(secret)) {
                            returnCommand.clear();
                            returnCommand.put("command", "LOGIN_SUCCESS");
                            returnCommand.put("username", username);
                            returnCommand.put("secret", secret);
                            System.out.println("LOGIN ALLOWED SEND");
                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                            allowed = true;
                            break;
                        }
                        if (loginInfo[0].equals(username) && !loginInfo[1].equals(secret)) {
                            allowed = true;
                            returnCommand.clear();
                            returnCommand.put("command", "LOGIN_FAILED");
                            returnCommand.put("username", username);
                            returnCommand.put("secret", secret);
                            returnCommand.put("info", "wrong username or password.");
                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                            System.out.println(returnCommand.toJSONString());
                            break;
                        }
                    }
                    if (allowed)
                        break;
                    else {
                        int loginIndex = userInList(username);
                        if (loginIndex >= 0) {
                            if (Control.getInstance().getRegUsers().get(loginIndex).getSecret().equals(secret) &&
                                    Control.getInstance().getRegUsers().get(loginIndex).isRegisterPass()) {
                                returnCommand.clear();
                                returnCommand.put("command", "LOGIN_SUCCESS");
                                returnCommand.put("username", username);
                                returnCommand.put("secret", secret);
                                System.out.println("LOGIN ALLOWED SEND");
                                flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                            } else {
                                returnCommand.clear();
                                returnCommand.put("command", "LOGIN_FAILED");
                                returnCommand.put("username", username);
                                returnCommand.put("secret", secret);
                                System.out.println("LOGIN FAILED SEND");
                                flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                            }
                        } else {
                            returnCommand.clear();
                            returnCommand.put("command", "NO_USER");
                            returnCommand.put("username", username);
                            returnCommand.put("secret", secret);
                            System.out.println("USER_NOT_FOUND");
                            flood(Control.getInstance().getServerCons(), returnCommand.toJSONString(), true);
                        }
                    }
                    break;
                case "NO_USER":
                    username = command.get("username").toString();
                    secret = command.get("secret").toString();
                    int checkNoUser = userInList(username);
                    if (checkNoUser >= 0) {
                        Control.getInstance().getRegUsers().get(checkNoUser).addCount();
                        System.out.println("Here the count is " + Control.getInstance().getRegUsers().get(checkNoUser).getLockcount());
                    } else
                        flood(Control.getInstance().getServerCons(), str, false);
                    break;
                case "LOGIN_SUCCESS":
                    boolean sendSuccess = false;
                    username = command.get("username").toString();
                    secret = command.get("secret").toString();
                    ArrayList<Connection> client = Control.getInstance().getConnections();
                    for (int i = 0; i < client.size(); i++) {
                        if (client.get(i).username.equals(username)) {
                            sendSuccess = true;
                            int userIndex = userInList(username);
                            Control.getInstance().getRegUsers().get(userIndex).setRegisterPass(true);
                            returnCommand.clear();
                            returnCommand.put("command", "LOGIN_SUCCESS");
                            returnCommand.put("info", "Login as " + username + " .");
                            client.get(i).writeBroadcast(returnCommand.toJSONString());
                            System.out.println("SET TRUE");
                            break;
                        }
                    }
                    if (!sendSuccess) {
                        LockCount user = new LockCount(username, secret, Control.getInstance().getServerAddresses().size());
                        user.setRegisterPass(true);
                        Control.getInstance().getRegUsers().add(user);
                        flood(Control.getInstance().getServerCons(), str, false);
                    } else {
                        sa = tryRedirect();
                        if (sa != null) {
                            sendRedirect(sa);
                        }
                    }
                    break;
                case "LOGIN_FAILED":
                    username = command.get("username").toString();
                    ArrayList<Connection> c = Control.getInstance().getConnections();
                    for (int i = 0; i < c.size(); i++) {
                        if (c.get(i).username.equals(username)) {
                            returnCommand.clear();
                            returnCommand.put("command", "LOGIN_FAILED");
                            returnCommand.put("info", "Wrong username or secret.");
                            c.get(i).writeBroadcast(returnCommand.toJSONString());

                        }
                    }
                    int ishere = userInList(username);
                    if (ishere >= 0) {
                        Control.getInstance().getRegUsers().remove(ishere);
                    } else {
                        flood(Control.getInstance().getServerCons(), str, false);
                    }
                    break;
                default:
                    returnCommand.clear();
                    returnCommand.put("command", "INVALID_MESSAGE");
                    returnCommand.put("info", "You are not authenticated. Now you will be kicked out.");
                    outd.write(returnCommand.toJSONString() + "\n");
                    outd.flush();
                    break;
            }

        }

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

    public int userInList(String username) {
        ArrayList<LockCount> lc = Control.getInstance().getRegUsers();
        for (int i = 0; i < lc.size(); i++) {
            if (lc.get(i).getUsername().equals(username))
                return i;
        }
        return -1;
    }

    public int serverInList(String ipAddress, int port) {
        ArrayList<ServerAddress> sa = Control.getInstance().getServerAddresses();
        for (int i = 0; i < sa.size(); i++) {
            if (sa.get(i).getIpAddress().equals(ipAddress) && sa.get(i).getPort() == port) {
                return i;
            }
        }
        return -1;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isOpen() {
        return open;
    }
}
