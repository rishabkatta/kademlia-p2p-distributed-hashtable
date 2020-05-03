/*
 * @author-name: Rishab Katta.
 *
 * Peer in Kademlia has a routing table and stores and retrieves file based on XoR metric between Peer and File HashIDs.
 */
package edu.rit.cs;

import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.client.*;
import com.thetransactioncompany.jsonrpc2.*;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.*;

//wrapper for all handlers for requests received on Port 6969.
class PeerRequestsHandler {

    // Implements a handler for "storeFile" JSON-RPC method
    public static class StoreFileHandler implements RequestHandler {


        // Reports the method names of the handled requests
        public String[] handledRequests() {

            return new String[]{"storeFile"};
        }


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("storeFile")) {

                // get the topic sent by EM from myParams and print it out.
                Map<String, Object> myParams = req.getNamedParams();
//                Gson gson = new Gson();
//                Topic topic = gson.fromJson(myParams.get("topic").toString(), Topic.class);
                String fileContent = (String) myParams.get("fileContent");
                String fileName = (String) myParams.get("fileName");
                Long lfileHashcode = (Long) myParams.get("fileHashcode");
                Integer fileHashcode = lfileHashcode.intValue();
                try (PrintWriter out = new PrintWriter("/home/rishabh/" + fileName)) {
                    out.println(fileContent);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                Peer.DHT.put(fileHashcode, fileName);
                return new JSONRPC2Response(fileHashcode, req.getID());

            } else { return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID()); }
        }
    }

    // Implements a handler for "retreiveFile" JSON-RPC method
    public static class RetreiveFileHandler implements RequestHandler {


        // Reports the method names of the handled requests
        public String[] handledRequests() {

            return new String[]{"retreiveFile"};
        }


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("retreiveFile")) {

                // get pending notifications list from myParams sent by EventManager.
                Map<String, Object> myParams = req.getNamedParams();
//                Gson gson = new Gson();
//                Type listType = new TypeToken<ArrayList<Object>>(){}.getType();
                Long lfileHashcode = (Long) myParams.get("fileHashcode");
                Integer fileHashcode = lfileHashcode.intValue();
                String fileName = Peer.DHT.get(fileHashcode);
                String filePath = "/home/rishabh/" + fileName;
                String fileContent = new String();
                try {
                     fileContent = new String(Files.readAllBytes(Paths.get(filePath)));
                } catch (IOException e) {
                    //Workaround for now
                    fileContent = "rishab";
                    fileName = "samplefile.txt";
                }
                String finalFileContent = fileContent;
                String finalFileName = fileName;
                HashMap<String,String> fileInfo = new HashMap<String, String>(){{
                    put("fileName", finalFileName);
                    put("fileContent", finalFileContent);
                }};
                return new JSONRPC2Response(fileInfo, req.getID());

            } else { return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());}
        }
    }

    // Implements a handler for "justCameOnline" JSON-RPC method.
    // When this method is called, Peer regenerates its RT & Moves files to new Peer if file is "closer" to the new peer.
    public static class JustCameOnlineHandler implements RequestHandler {


        // Reports the method names of the handled requests
        public String[] handledRequests() {

            return new String[]{"justCameOnline"};
        }


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {
            ArrayList<Integer> listOfKeysToRemoveFromDHT = new ArrayList<>();
            if (req.getMethod().equals("justCameOnline")) {
                try {
                    Peer.generateRoutingTable();
                    Map<String, Object> myParams = req.getNamedParams();
                    Long lnewPeerID = (Long) myParams.get("nodeID");
                    Integer newPeerID = lnewPeerID.intValue();
                    String newPeerIPAddress = (String) myParams.get("IPAddress");
                    URL serverURL = null;
                    try {
                        serverURL = new URL("http://" + newPeerIPAddress + ":" + 6969);

                    } catch (MalformedURLException ignored) {
                    }
                    JSONRPC2Session mySession = new JSONRPC2Session(serverURL);

                    //among the list of managed keys, if key's hashID closer to newPeerID than it is our current node ID
                    //move that key there.
                    for(Map.Entry<Integer, String> dhtEntry: Peer.DHT.entrySet()){
                        int fileHashcode = dhtEntry.getKey() % 16;
                        if ((fileHashcode ^ newPeerID) < (fileHashcode ^ Peer.nodeID)){
                            //file to be stored at the new Peer.
                            String fileName = dhtEntry.getValue();
                            String fileContent = new String(Files.readAllBytes(Paths.get("/home/rishabh/" + fileName)));

                            Peer.requestID += 1;
                            JSONRPC2Request request = new JSONRPC2Request("storeFile", Peer.requestID);
                            Map<String, Object> readjustParams = new HashMap<>();
                            readjustParams.put("fileContent", fileContent);
                            readjustParams.put("fileName", fileName);
                            readjustParams.put("fileHashcode", dhtEntry.getKey());
                            request.setNamedParams(readjustParams);
                            JSONRPC2Response response = null;
                            try {
                                response = mySession.send(request);
                            } catch (JSONRPC2SessionException ignored) {
                                System.out.println("File couldn't be stored at the peer.");
                            }
                            if (response != null && response.indicatesSuccess()) {
                                listOfKeysToRemoveFromDHT.add(dhtEntry.getKey());
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                for (Integer key: listOfKeysToRemoveFromDHT) {
                    Peer.DHT.remove(key);
                }
                return new JSONRPC2Response("routing table updated", req.getID());

            } else { return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());}
        }
    }

    // Implements a handler for "dyingBye" JSON-RPC method.
    //Regenrates it's RT & Saves files to new Peer (which is this peer who received the request).
    public static class DyingByeHandler implements RequestHandler {


        // Reports the method names of the handled requests
        public String[] handledRequests() {

            return new String[]{"dyingBye"};
        }


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("dyingBye")) {
                Map<String, Object> myParams = req.getNamedParams();

                try {
                    Peer.generateRoutingTable();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                if (myParams.containsKey("filesMap")){
                    Gson gson = new Gson();
                    Type mapType = new TypeToken<HashMap<String, HashMap<String,String>>>(){}.getType();
                    HashMap<String, HashMap<String,String>> filesMap = gson.fromJson(myParams.get("filesMap").toString(), mapType);
                    for (Map.Entry<String, HashMap<String,String>> filesMapEntry: filesMap.entrySet()){
                        String fileName = filesMapEntry.getKey();
                        String fileContent = filesMapEntry.getValue().get("fileContent");
                        Integer fileHashcode = Integer.parseInt(filesMapEntry.getValue().get("fileHashcode"));
                        try (PrintWriter out = new PrintWriter("/home/rishabh/" + fileName)) {
                            out.println(fileContent);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                        Peer.DHT.put(fileHashcode, fileName);
                    }
                }

                return new JSONRPC2Response("routing table updated", req.getID());

            } else { return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());}
        }
    }
}

//Thread which dispatches methods from PeerRequestsHandler accordingly.
class PHandler extends Thread {
    private String name;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Dispatcher dispatcher;

    /**
     * Constructs a handler thread, squirreling away the socket.
     * All the interesting work is done in the run method.
     */
    public PHandler(Socket socket) {
        this.socket = socket;

        // Create a new JSON-RPC 2.0 request dispatcher
        this.dispatcher = new Dispatcher();

        // Register the "echo", "receiveTopic" and "receivePendingNotifications" handlers with it
        dispatcher.register(new PeerRequestsHandler.StoreFileHandler());
        dispatcher.register(new PeerRequestsHandler.RetreiveFileHandler());
        dispatcher.register(new PeerRequestsHandler.JustCameOnlineHandler());
        dispatcher.register(new PeerRequestsHandler.DyingByeHandler());
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
                System.out.println("Exception occured when trying to close socket connection.");
            }
        }
    }
}


//Just a helper class for sorting neighbors based on XoR from current nodeID.
class comparator implements Comparator<Integer>{
    int nodeID;
    comparator(int n){
        this.nodeID = n;
    }

    @Override
    public int compare(Integer o1, Integer o2) {
        return Integer.compare(o1^nodeID, o2^nodeID);
    }
}

//Peer "client" class which makes requests to SN, Clients etc. to Generate it's RT, Store/retrieve files etc.
public class Peer {

    public static JSONRPC2Session mainSuperNodeSession = null;
    public static int requestID = 0;
    public static int nodeID;
    public static LinkedHashMap<String, HashMap<String, String>> ROUTING_TABLE = new LinkedHashMap<>();
    public static HashMap<Integer, String> DHT = new HashMap<>();

    /*
     * create a session(with the EventManager) object and assign it to a static variable.
     */
    public static void createSNConnection(String superNodeHostname, int superNodePort){
        URL serverURL = null;

        try {
            serverURL = new URL("http://"+superNodeHostname+":"+superNodePort);
        } catch (MalformedURLException e) {
            System.out.println("SN not up.");
        }

        mainSuperNodeSession = new JSONRPC2Session(serverURL);
    }

    private static void traverse(Node node, ArrayList<Integer> potentialNeighbors){
        if(node == null)
            return;
        traverse(node.zero, potentialNeighbors);
        traverse(node.one, potentialNeighbors);
        if(node.value != null) {
            potentialNeighbors.add(node.value);
        }
    }


    public static ArrayList<Integer> orderNeighborsBasedOnXor(ArrayList<Integer> potentialNeighbors){
        PriorityQueue<Integer> pq = new PriorityQueue<>(10 , new comparator(nodeID));
        ArrayList<Integer> reOrderedNeighbors = new ArrayList<>();
        pq.addAll(potentialNeighbors);
        while(pq.size() >0)
            reOrderedNeighbors.add(pq.poll());
        return reOrderedNeighbors;
    }

    public static ArrayList<Integer> getPotentialNeighbors(Node root, String bin_repr){
        int index = 0;
        ArrayList<Integer> potentialNeighbors = new ArrayList<>();
        while(index < bin_repr.length() && bin_repr.charAt(index) != 'x') {
            root = bin_repr.charAt(index) == '0' ? root.zero : root.one;
            index++;
        }
        traverse(root, potentialNeighbors);
        return potentialNeighbors;
    }

    public static ArrayList<String> getKBucketKeys(String bin_string){
        String temp_bin_string;
        ArrayList<String> kBucketKeys = new ArrayList<>();
        kBucketKeys.add(bin_string);
        for (int i=1; i<= 4; i++){
            temp_bin_string = bin_string.substring(0,bin_string.length()-i) + StringUtils.repeat("x", i);
            kBucketKeys.add(temp_bin_string);
            bin_string=temp_bin_string;
        }
        return kBucketKeys;
    }

    public static String convertIntegerToBinaryString(int hashID){
        String bin_string = Integer.toBinaryString(hashID);
        while (bin_string.length()!=4){
            bin_string = "0" + bin_string;
        }
        return bin_string;
    }

    //get online peers from two SNs.
    public static HashMap<String, String> getOnlinePeers(){
        String method = "getOnlinePeers";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        // Send getAllTopics request to the EM and get response.
        JSONRPC2Response response = null;

        try {
            response = mainSuperNodeSession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        String hostname = "";
        hostname = nodeID==2 ? Config.SUPERNODE4_IP : Config.SUPERNODE2_IP;

        //connect to other supernode
        URL serverURL = null;
        try {
            serverURL = new URL("http://" + hostname + ":9091");

        } catch (MalformedURLException ignored) {
        }
        JSONRPC2Session newSession = new JSONRPC2Session(serverURL);
        JSONRPC2Response newresponse = null;
        try {
            newresponse = newSession.send(request);
        } catch (JSONRPC2SessionException ignored) {
        }

        // Print response result / error
        HashMap<String, String> onlinePeersMap = new HashMap<>();
        if (response != null && response.indicatesSuccess() && newresponse != null && newresponse.indicatesSuccess()){
            Gson gson = new Gson();
            //Type mapType = new TypeToken<HashMap<String, Topic>>(){}.getType();
            onlinePeersMap =  gson.fromJson(response.getResult().toString(), HashMap.class);
            onlinePeersMap.putAll(gson.fromJson(newresponse.getResult().toString(), HashMap.class));

        }
        return onlinePeersMap;
    }

    //generates RT.
    public static void generateRoutingTable() throws UnknownHostException {

        Node root = new BinaryTree().populateBinaryTree(new Node(), 0, "0");

        String bin_string = convertIntegerToBinaryString(nodeID);
        ArrayList<String> kBucketKeys = getKBucketKeys(bin_string);

        //populate intial routing table
        for (String kBucketKey : kBucketKeys) { //[1101, 110x, 11xx, 1xxx, xxxx]
            if (kBucketKey.equals(bin_string)){
                ROUTING_TABLE.put(kBucketKey, new HashMap<String, String>(){{
                    put("nodeID", String.valueOf(nodeID));
                    put("IPAddress", InetAddress.getLocalHost().getHostAddress());
                }});
            }
            else{
                ROUTING_TABLE.put(kBucketKey, null);
            }
        }

        HashMap<String, String> onlinePeers = getOnlinePeers();


        ArrayList<Integer> previousPotentialneighbors = new ArrayList<>();
        previousPotentialneighbors.add(nodeID);//[10]
        for (String kBucketKey: kBucketKeys) {
            //kBucketKey = 101x
            ArrayList<Integer> potentialNeighbors = orderNeighborsBasedOnXor(getPotentialNeighbors(root, kBucketKey));//potentialNeighbors = [10,11]
            potentialNeighbors.removeAll(previousPotentialneighbors); //[11]
            previousPotentialneighbors.addAll(potentialNeighbors); //[10,11]
            for (Integer neighborNodeID : potentialNeighbors) {
                if (onlinePeers.containsKey(String.valueOf(neighborNodeID))){
                    //replace null with new neighbor node nnd.
                    ROUTING_TABLE.replace(kBucketKey, new HashMap<String, String>(){{
                        put("nodeID", String.valueOf(neighborNodeID));
                        put("IPAddress", onlinePeers.get(String.valueOf(neighborNodeID)));
                    }});
                  break;
                }
            }
            }
    }

    //match Pefix.
    public String prefixMatch(int fileHashID){
        String fileID_bin_string = convertIntegerToBinaryString(fileHashID);
        for ( String key : ROUTING_TABLE.keySet() ){
            int count = 0;
            for (int i = 0; i < key.length(); i++){
                char f = fileID_bin_string.charAt(i);
                char r = key.charAt(i);
                if (r == f || r=='x'){
                    count += 1;
                    continue;
                }else
                    break;
            }
            if (count == fileID_bin_string.length()){
                return key;
            }
        }
        return "xxxx";
    }

    //make a justCameOnline Request to other Concerned Peers.
    public static void sendJustCameOnlineToNeighbors() throws UnknownHostException {
        //send just came online info to other peers and super node
        String method = "justCameOnline";
        String IPAddress = InetAddress.getLocalHost().getHostAddress();
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("nodeID", nodeID);
        myParams.put("IPAddress", IPAddress);
        request.setNamedParams(myParams);

        // Send request and populate response from EM.
        JSONRPC2Response response = null;

        try {
            //send justCameOnline to SuperNode.
            response = mainSuperNodeSession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        URL serverURL = null;

        for(Map.Entry<String, HashMap<String, String>> rtEntry: ROUTING_TABLE.entrySet()){
            if (!rtEntry.getKey().equals(convertIntegerToBinaryString(nodeID)) && rtEntry.getValue() != null){
                String neighborIP = rtEntry.getValue().get("IPAddress");

                try {
                    serverURL = new URL("http://" + neighborIP + ":" + 6969);
                } catch (MalformedURLException e) {
                    System.out.println("client not up.");
                }
                JSONRPC2Session newSession = new JSONRPC2Session(serverURL);
                Peer.requestID += 1;
                JSONRPC2Request newRequest = new JSONRPC2Request(method, Peer.requestID);
                newRequest.setNamedParams(myParams);
                JSONRPC2Response newResponse = null;
                try {
                    newResponse = newSession.send(newRequest);
                } catch (JSONRPC2SessionException ignored) {
                }
            }
        }
        System.out.println("Broadcasted Online status to concerned peers.");
    }

    //make dyingBye Request to other Concerned Peers.
    public static void sendDyingByeToNeighbors() throws IOException {
        //send dying bye info to other peers and super node
        String method = "dyingBye";
        String ipAddress = InetAddress.getLocalHost().getHostAddress();
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("nodeID", nodeID);
        myParams.put("IPAddress", ipAddress);
        request.setNamedParams(myParams);

        // Send request and populate response from EM.
        JSONRPC2Response response = null;

        try {
            //send dyingBye to SuperNode.
            response = mainSuperNodeSession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        //send dyingBye to neighborPeers.
        boolean nearestNeighborFlag = true;
        for(Map.Entry<String, HashMap<String, String>> rtEntry: ROUTING_TABLE.entrySet()){

            if (!rtEntry.getKey().equals(convertIntegerToBinaryString(nodeID)) && rtEntry.getValue() != null){
                String neighborIP = rtEntry.getValue().get("IPAddress");
                URL serverURL = null;
                try {
                    serverURL = new URL("http://" + neighborIP + ":" + 6969);

                } catch (MalformedURLException e) {
                    System.out.println("client not up.");
                }
                JSONRPC2Session newSession = new JSONRPC2Session(serverURL);
                Peer.requestID += 1;
                JSONRPC2Request newRequest = new JSONRPC2Request(method, Peer.requestID);
                //send all files to nearest neighbor before dying.
                if (nearestNeighborFlag){
                    HashMap<String, HashMap<String, String> > filesMap = new HashMap<>();
                    for (Map.Entry<Integer, String> dhtEntry: Peer.DHT.entrySet()){
                        //filename is key. and fileHashcode is string here.
                        filesMap.put(dhtEntry.getValue(), new HashMap<String, String>(){{
                            put("fileContent", new String(Files.readAllBytes(Paths.get("/home/rishabh/" + dhtEntry.getValue()))));
                            put("fileHashcode", String.valueOf(dhtEntry.getKey()));
                        }});
                    }
                    myParams.put("filesMap", filesMap);
                    nearestNeighborFlag = false;
                }
                newRequest.setNamedParams(myParams);
                JSONRPC2Response newResponse = null;
                try {
                    newResponse = newSession.send(newRequest);
                } catch (JSONRPC2SessionException ignored) {
                }
            }
        }
        System.out.println("Broadcasted Offline status to concerned peers.");
    }

    //for Peer "Server" to handle requests from Peer "Clients"
    public void listenToRequestsFromClients() throws IOException {
        try (ServerSocket listener = new ServerSocket(6969)) {
            while (true) {
                new PHandler(listener.accept()).start();
            }
        }
    }


    //driver code.
    public static void main(String[] args) throws IOException {
        Peer aPeer = new Peer();
        new Thread(() -> {
            try {
                aPeer.listenToRequestsFromClients(); //do we want one or multiple instances running?
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        Scanner sc = new Scanner(System.in);
        requestID = new Random().nextInt(5000);
        nodeID = Integer.parseInt(args[0]);
        String hostname;
        if ((nodeID ^ 2) < (nodeID ^ 4)){
           hostname = Config.SUPERNODE2_IP;
        }else {
            hostname = Config.SUPERNODE4_IP;
        }


        createSNConnection(hostname, 9091);
        generateRoutingTable();
        sendJustCameOnlineToNeighbors();


        while (true){
            System.out.println("==================================================");
            System.out.println("\n 1. Show Routing Table \n 2. " +
                    "Show a list of Managed Keys \n 3. Exit \n");
            System.out.println("===================================================");
            String userChoice = sc.nextLine();
            while (!userChoice.equals("1") && !userChoice.equals("2")&& !userChoice.equals("3") ){
                System.out.println("Please enter 1 to Show Routing Table / 2 to List Keys / 3 to Exit ");
                userChoice = sc.nextLine();
            }
            switch (userChoice) {
                case "1": {
                    for(Map.Entry<String, HashMap<String, String>> rtEntry: ROUTING_TABLE.entrySet()){
                        System.out.println(rtEntry.getKey() + ":" + rtEntry.getValue());
                    }
                    break;
                }
                case "2": {
                    for (Map.Entry<Integer, String> dhtEntry: Peer.DHT.entrySet()){
                        System.out.println(dhtEntry.getKey());
                    }
                    break;
                }
                case "3":
                    sendDyingByeToNeighbors();
                    System.exit(0);
                    break;
                default:
                    System.exit(0);
            }
        }
    }
}
