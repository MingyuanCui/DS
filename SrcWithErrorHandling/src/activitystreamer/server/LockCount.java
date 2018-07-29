package activitystreamer.server;

public class LockCount {
    private String username;
    private int lockcount;
    private String secret;
    private boolean registerPass = false;

    public LockCount(String username, String secret, int lockcount) {
        this.lockcount = lockcount;
        this.username = username;
        this.secret = secret;
    }

    public void addCount() {
        this.lockcount += 1;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSecret() {
        return secret;
    }

    public void setRegisterPass(boolean pass) {
        this.registerPass = true;
    }

    public boolean isRegisterPass() {
        return this.registerPass;
    }

    public int getLockcount() {
        return lockcount;
    }

    public String getUsername() {
        return username;
    }
}
