package activitystreamer.server;

public class ServerAddress {
    private String ipAddress;
    private int port;
    private int load;

    public ServerAddress(String ipAddress, int port, int load) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.load = load;
    }

    public ServerAddress(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
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
