/**
 * @author-name: Rishab Katta
 * CSCI-652. Project 3. Kademlia Distributed Hashtable.
 *
 * Supernode is the bootstrapping node where all the client requests go through.
 */
package edu.rit.cs;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

class SuperNodeHandler {

    //Implements a Handler for updating status of a Peer.
    public static class StatusRequestHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {
            return new String[]{"getOnlinePeers"};
        }

        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("getOnlinePeers")) {
                HashMap<Integer, String> onlinePeers = new HashMap<>();
                for (Map.Entry<Integer, String> peerStatusEntry :
                        SuperNode.registryOfOnlinePeers.entrySet()) {
                    if (peerStatusEntry.getValue() != null) {
                        onlinePeers.put(peerStatusEntry.getKey(), peerStatusEntry.getValue());
                    }
                }
                return new JSONRPC2Response(onlinePeers, req.getID());

            } else {
                return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
            }
        }
    }

    //Implements a Handler for updating status of a Peer.
    public static class JustCameOnlineHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {
            return new String[]{"justCameOnline"};
        }

        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("justCameOnline")) {
                Map<String, Object> myParams = req.getNamedParams();
                Long lnodeID = (Long) myParams.get("nodeID");
                Integer nodeID = lnodeID.intValue();
                String IPAddress = (String) myParams.get("IPAddress");

                if (SuperNode.registryOfOnlinePeers.containsKey(nodeID)) {
                    //NodeID already registered in SuperNode
                    SuperNode.registryOfOnlinePeers.replace(nodeID, IPAddress);
                } else {
                    //Node just entered the network for the first time.
                    SuperNode.registryOfOnlinePeers.put(nodeID, IPAddress);
                }

                return new JSONRPC2Response("peerStatusMap in SuperNode is updated.", req.getID());

            } else {
                return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
            }
        }
    }

    //Implements a Handler for updating status of a Peer.
    public static class DyingByeHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {
            return new String[]{"dyingBye"};
        }

        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("dyingBye")) {
                Map<String, Object> myParams = req.getNamedParams();
                Long lnodeID = (Long) myParams.get("nodeID");
                Integer nodeID = lnodeID.intValue();

                if (SuperNode.registryOfOnlinePeers.containsKey(nodeID)) {
                    //NodeID already registered in SuperNode
                    SuperNode.registryOfOnlinePeers.replace(nodeID, null);
                } else {
                    //Node just entered the network for the first time.
                    SuperNode.registryOfOnlinePeers.put(nodeID, null);
                }

                return new JSONRPC2Response("peerStatusMap in SuperNode is updated.", req.getID());

            } else {
                return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
            }
        }
    }
}

public class SuperNode extends Peer {
    public static HashMap<Integer, String> registryOfOnlinePeers = new HashMap<>();
    public static int requestID = 0;

    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private Dispatcher dispatcher;

        /**
         * Constructs a handler thread, squirreling away the socket.
         * All the interesting work is done in the run method.
         */
        public Handler(Socket socket) {
            this.socket = socket;

            // Create a new JSON-RPC 2.0 request dispatcher
            this.dispatcher = new Dispatcher();

            // Register all the Handlers with the dispatcher.
            dispatcher.register(new SuperNodeHandler.DyingByeHandler());
            dispatcher.register(new SuperNodeHandler.StatusRequestHandler());
            dispatcher.register(new SuperNodeHandler.JustCameOnlineHandler());
        }

        /**
         * Services this thread's client by repeatedly requesting a
         * screen name until a unique one has been submitted, then
         * acknowledges the name and registers the output stream for
         * the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {
                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // read request
                String line;
                line = in.readLine();
                //System.out.println(line);
                StringBuilder raw = new StringBuilder();
                raw.append("" + line);
                boolean isPost = line.startsWith("POST");
                int contentLength = 0;
                while (!(line = in.readLine()).equals("")) {
                    //System.out.println(line);
                    raw.append('\n' + line);
                    if (isPost) {
                        final String contentHeader = "Content-Length: ";
                        if (line.startsWith(contentHeader)) {
                            contentLength = Integer.parseInt(line.substring(contentHeader.length()));
                        }
                    }
                }
                StringBuilder body = new StringBuilder();
                if (isPost) {
                    int c = 0;
                    for (int i = 0; i < contentLength; i++) {
                        c = in.read();
                        body.append((char) c);
                    }
                }

                JSONRPC2Request request = JSONRPC2Request.parse(body.toString());
                JSONRPC2Response resp = dispatcher.process(request, null);

                // send response
                out.write("HTTP/1.1 200 OK\r\n");
                out.write("Content-Type: application/json\r\n");
                out.write("\r\n");
                out.write(resp.toJSONString());
                out.flush();
                out.close();
                socket.close();
            } catch (IOException e) {
                System.out.println(e);
            } catch (JSONRPC2ParseException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }


    //Start a listener which listens to request from the clients.
    private void listenToRequestFromPeers() throws IOException {
        ServerSocket listener = new ServerSocket(Config.SN_MAIN_PORT);
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }


    //main is used handle CLI, instantiate EM and start threads listening to requests from clients in the background.
    public static void main(String[] args) throws IOException {
        SuperNode sn = new SuperNode();

        new Thread(() -> {
            try {
                sn.listenToRequestFromPeers();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                sn.listenToRequestsFromClients();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        requestID = new Random().nextInt(5000);
        nodeID = Integer.parseInt(args[0]);

        createSNConnection(InetAddress.getLocalHost().getHostAddress(), Config.SN_MAIN_PORT);
        generateRoutingTable();
        sendJustCameOnlineToNeighbors();

        System.out.println("\nSuper Node is up and running on: " +
                InetAddress.getLocalHost().getHostAddress() + ":" + Config.SN_MAIN_PORT + "\n");
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("==================================================");
            System.out.println("\n 1. Show a list of Online Peers \n 2. " +
                    "Show Routing Table\n 3. Show a list of Managed Keys \n");
            System.out.println("===================================================");
            String userChoice = sc.nextLine();
            while (!userChoice.equals("1") && !userChoice.equals("2") && !userChoice.equals("3")) {
                System.out.println("Please enter 1 to Show Routing Table / 2 to List Keys / 3 to Exit ");
                userChoice = sc.nextLine();
            }
            switch (userChoice) {
                case "1": {

                    for (Map.Entry<Integer, String> peerStatusMapEntry : SuperNode.registryOfOnlinePeers.entrySet()) {
                        System.out.println(peerStatusMapEntry.getKey() + ":" + peerStatusMapEntry.getValue());
                    }

                    break;
                }
                case "2": {

                    for (Map.Entry<String, HashMap<String, String>> rtEntry : ROUTING_TABLE.entrySet()) {
                        System.out.println(rtEntry.getKey() + ":" + rtEntry.getValue());
                    }
                    break;
                }
                case "3":
                    for (Map.Entry<Integer, String> dhtEntry : Peer.DHT.entrySet()) {
                        System.out.println(dhtEntry.getKey());
                    }
                    break;
                default:
                    System.exit(0);
            }
        }
    }
}
