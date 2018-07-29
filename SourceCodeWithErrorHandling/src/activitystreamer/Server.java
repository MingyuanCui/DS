package activitystreamer;

import java.util.Scanner;

import activitystreamer.util.Settings;
import org.apache.commons.cli.*;

import activitystreamer.server.Control;

public class Server {
    private static void help(Options options) {
        String header = "An ActivityStream Server for Unimelb COMP90015\n\n";
        String footer = "\nGroup: Diablo IV";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ActivityStreamer.Server", header, options, footer, true);
        System.exit(-1);
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("lp", true, "local port to listen to, default 4500");
        options.addOption("rp", true, "remote port number");
        options.addOption("rh", true, "remote hostname,default localhost");
        options.addOption("s", true, "secret for username");

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
                Settings.setRemotePort(port);
            } catch (NumberFormatException e) {
                help(options);
            }
        }

        if (cmd.hasOption("s")) {
            Settings.setSecret(cmd.getOptionValue("s"));
        }

        if (cmd.hasOption("lp")) {
            try {
                int port = Integer.parseInt(cmd.getOptionValue("lp"));
                Settings.setLocalPort(port);
            } catch (NumberFormatException e) {
                help(options);
            }
        }

        if (Settings.getRemoteHostname().equals(Settings.getLocalHostname()) && Settings.getRemotePort() == Settings.getLocalPort()) {
            System.out.println("Remote socket can't be the same as local socket.");
            System.exit(-1);
        }

        Control control = Control.getInstance();
        if (Settings.getRemotePort() != -1) control.initiateConnection();

    }


}
