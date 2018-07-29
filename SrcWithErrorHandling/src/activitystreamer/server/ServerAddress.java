package activitystreamer.server;

public class ServerAddress {
    private String ipAddress;
    private int port;
    private int load;
    private String announceID = "1995-01-01 00:00:00";

    public ServerAddress(String ipAddress, int port, int load) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.load = load;
        //InetAddress addr = InetAddress.getByName( "127.0.0.1" );
    }

    public ServerAddress(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public void setAnnounceID(String announceID)
    {
        this.announceID = announceID;
    }

    public String getAnnounceID() {
        return announceID;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public int getPort() {
        return this.port;
    }

    public void setLoad(int load) {
        this.load = load;
    }

    public int getLoad() {
        return this.load;
    }
}
