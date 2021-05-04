package simplechat.communication.socket.client;

import simplechat.client.SimpleChat;
import simplechat.communication.MessageProtocol;

import static simplechat.communication.MessageProtocol.Commands.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static java.util.logging.Level.*;

/**
 * SimpleChatClient connects to SimpleChatServer with the choosen communication protocol and initiates a UI.
 * <br>
 * Default settings for the main attributes will be: name="Client" host="localhost" and port=5050
 */
public class SimpleChatClient extends Thread {

    private String name = "Client";
    private String host = "localhost";
    private Integer port = 5050;

    private InetSocketAddress socketAddress;
    private Socket socket = null;
    private PrintWriter out;
    private BufferedReader in;

    private boolean listening = false;
    private String currentMessage;

    private SimpleChat client;

    /**
     * Initializes host, port and callback for UserInterface interactions.
     *
     * @param name   String representation of chatName
     * @param host   String representation of hostname, on which the server should listen
     * @param port   Integer for the listening port
     * @param client UserInterface callback reference for user interactions
     */
    public SimpleChatClient(String name, String host, Integer port, SimpleChat client) {
        if (name != null) this.name = name;
        if (host != null) this.host = host;
        if (port != null) this.port = port;
        this.client = client;
        SimpleChat.clientLogger.log(INFO, "Init: host=" + this.host + " port="
                + this.port + " chatName=" + this.name);
    }

    /**
     * Initiating the Socket with already defined Parameters (host, port). Also a timeout of 2000 ms is set at connect.
     * The {@link java.net.Socket#setKeepAlive(boolean)} is set to true.
     * <br>
     * After activating {@link #listening}, the Chatname will be sent to the Server and the reading loop is started,
     * checking for the {@link BufferedReader#readLine()} and the {@link #listening} flag.
     * <br>
     * In case of an Exception the Thread will be interrupted and if the socket was connected and bound,
     * the {@link #shutdown()} method will be called.
     */
    public void run() {
        try {
            socketAddress = new InetSocketAddress(InetAddress.getByName(host), port);
            socket = new Socket();
            socket.connect(socketAddress, 2000);
            socket.setKeepAlive(true);

            in = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);

            SimpleChat.clientLogger.log(INFO, "Connected to Server: socket=" + socket + " in=" + in + " out=" + out);
            listening = true;
            send(MessageProtocol.getMessage(CHATNAME) + " " + name);

            while ((currentMessage = in.readLine()) != null && listening) {
                SimpleChat.clientLogger.log(INFO, "Received from server: " + currentMessage);
                received();
            }
        } catch (IOException io) {
            if (io.getMessage().equals("Connection refused (Connection refused)")) {
                this.interrupt();
            }
            if (io.getMessage().equals("Stream closed")) {
                this.interrupt();
            } else
                SimpleChat.clientLogger.log(SEVERE, io.toString());
        } finally {
            if (socket.isBound()) shutdown();
        }
    }

    /**
     * Analyzing received messages.
     * <br>
     * If Server sends proper {@link simplechat.communication.MessageProtocol.Commands} this method will act accordingly.
     * <br>
     * {@link simplechat.communication.MessageProtocol.Commands#EXIT} will set listening to false
     * and then calls {@link #shutdown()}
     * <br>
     * If there is now Command (no "!" as first character),
     * the message will be passed to {@link simplechat.client.SimpleChat#incomingMessage(String)}
     */
    private void received() {
        if (currentMessage.startsWith("!")) {
            try {
                switch (MessageProtocol.getCommand(currentMessage.split(" ")[0])) {
                    case EXIT:
                        listening = false;
                        shutdown();
                        client.incomingMessage("Server has closed the connection!");
                        break;
                    default:
                }
            } catch (IllegalArgumentException iae) {
                SimpleChat.clientLogger.log(SEVERE, iae.toString());
            }
        } else {
            SimpleChat.clientLogger.log(INFO, "Passing message to client UI ...");
            client.incomingMessage(currentMessage);
        }
    }

    /**
     * Sending message to the server through network
     *
     * @param message Public message for server intercommunication
     */
    public void send(String message) {
        try {
            if (socket.isConnected()) {
                SimpleChat.clientLogger.log(INFO, "Sending message to server: " + message);
                out.println(message);
                out.flush();
            }
        } catch (Exception e) {
            SimpleChat.clientLogger.log(SEVERE, e.toString());
            if (!socket.isClosed()) shutdown();
        }
    }

    /**
     * Sending message to the server through network for private Message
     *
     * @param message  Private message for client-to-client intercommunication
     * @param chatName Name of receiver
     */
    public void send(String message, String chatName) {
        //TODO implement private messaging
    }

    /**
     * Clean shutdown of Client
     * <br>
     * If listening was still true, we are sending a {@link MessageProtocol.Commands#EXIT} to the server.
     * Finally we are closing all open resources.
     */
    public void shutdown() {
        SimpleChat.clientLogger.log(INFO, "Shutting down Client ... listening=" + listening);
        if (listening) {
            listening = false;
            send(MessageProtocol.getMessage(EXIT));
            client.incomingMessage("Closed connection to server!");
        }
        if (socket.isConnected())
            try {
                in.close();
                out.close();
                socket.close();
            } catch (Exception e) {
                SimpleChat.clientLogger.log(SEVERE, e.toString());
            } finally {
                this.interrupt();
            }
    }

    /**
     * @return True if still listening and online
     */
    public boolean isListening() {
        return listening;
    }
}