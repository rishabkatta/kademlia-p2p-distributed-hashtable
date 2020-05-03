package edu.rit.cs;

/**
 * @author rishab katta
 *
 * Client in Kademlia makes two requests. Store a file and Retreive a file.
 */

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.*;


public class Client {

    public static int requestID = 0;

    public void traverse(Node node, ArrayList<Integer> potentialNeighbors) {
        if (node == null)
            return;
        traverse(node.zero, potentialNeighbors);
        traverse(node.one, potentialNeighbors);
        if (node.value != null)
            potentialNeighbors.add(node.value);
    }

    public ArrayList<Integer> getPotentialNeighbors(Node root, String bin_repr) {
        int index = 0;
        ArrayList<Integer> potentialNeighbors = new ArrayList<>();
        while (index < bin_repr.length() && bin_repr.charAt(index) != 'x') {
            root = bin_repr.charAt(index) == '0' ? root.zero : root.one;
            index++;
        }
        traverse(root, potentialNeighbors);
        return potentialNeighbors;
    }

    public int getHashcode(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        return content.hashCode() % 16;
    }

    public ArrayList<String> getKBucketKeys(String bin_string) {
        String temp_bin_string;
        ArrayList<String> kBucketKeys = new ArrayList<>();
        kBucketKeys.add(bin_string);
        for (int i = 1; i <= 4; i++) {
            temp_bin_string = bin_string.substring(0, bin_string.length() - i) + StringUtils.repeat("x", i);
            kBucketKeys.add(temp_bin_string);
            bin_string = temp_bin_string;
        }
        return kBucketKeys;
    }

    public HashMap<String, String> getOnlinePeers() {
        String method = "getOnlinePeers";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        // Send getAllTopics request to the EM and get response.
        //connect to other supernode
        URL serverURL = null;
        try {
            serverURL = new URL("http://" + Config.SUPERNODE2_IP + ":9091");
        } catch (MalformedURLException ignored) {
        }
        JSONRPC2Session sn1Session = new JSONRPC2Session(serverURL);
        JSONRPC2Response sn1Response = null;

        try {
            sn1Response = sn1Session.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        try {
            serverURL = new URL("http://" + Config.SUPERNODE4_IP + ":9091");
        } catch (MalformedURLException ignored) {
        }
        JSONRPC2Session sn2Session = new JSONRPC2Session(serverURL);
        JSONRPC2Response sn2Response = null;
        try {
            sn2Response = sn2Session.send(request);
        } catch (JSONRPC2SessionException ignored) {
        }

        // Print response result / error
        HashMap<String, String> onlinePeersMap = new HashMap<>();
        if (sn1Response != null && sn1Response.indicatesSuccess() && sn2Response != null && sn2Response.indicatesSuccess()) {
            Gson gson = new Gson();
            //Type mapType = new TypeToken<HashMap<String, Topic>>(){}.getType();
            onlinePeersMap = gson.fromJson(sn1Response.getResult().toString(), HashMap.class);
            onlinePeersMap.putAll(gson.fromJson(sn2Response.getResult().toString(), HashMap.class));
        }
        return onlinePeersMap;
    }

    public String convertIntegerToBinaryString(int hashID) {
        String bin_string = Integer.toBinaryString(hashID);
        while (bin_string.length() != 4) {
            bin_string = "0" + bin_string;
        }
        return bin_string;
    }

    public HashMap<String, String> getClosestPeerInfo(int fileHashID) {

        //you are looking for file in an ID "closer" to fileID but moving all the files
        //to an ID "closer" to node ID? WHAT THE FUCK?

        HashMap<String, String> onlinePeersMap = getOnlinePeers();
        String fileID_bin_string = convertIntegerToBinaryString(fileHashID); //1101
        ArrayList<String> kBucketKeys = getKBucketKeys(fileID_bin_string);//[1101, 110x, 11xx, 1xxx, xxxx]
        Node root = new BinaryTree().populateBinaryTree(new Node(), 0, "0");

        HashMap<String, String> resultMap = new HashMap<>();
        for (String kBucketKey : kBucketKeys) { //[1101]
            ArrayList<Integer> potentialNeighbors = getPotentialNeighbors(root, kBucketKey);//[8]
            boolean foundNeighborToStoreFile = false;
            for (Integer potentialNeighbor : potentialNeighbors) {
                if (onlinePeersMap.containsKey(String.valueOf(potentialNeighbor))) {
                    resultMap.put("peerID", String.valueOf(potentialNeighbor));
                    resultMap.put("peerIPAddress", onlinePeersMap.get(String.valueOf(potentialNeighbor)));
                    foundNeighborToStoreFile = true;
                }
            }
            if (foundNeighborToStoreFile) {
                break;
            }
        }
        return resultMap;
    }

    //make a store file request to Peer.
    public void storeFile(HashMap<String, String> peerToStoreFileMap, String filePath) throws IOException {
        //now send file to neighborIP
        String peerID = peerToStoreFileMap.get("peerID");
        String peerIPAddress = peerToStoreFileMap.get("peerIPAddress");
        URL serverURL = null;
        try {
            serverURL = new URL("http://" + peerIPAddress + ":" + 6969);

        } catch (MalformedURLException ignored) {
        }
        JSONRPC2Session pSession = new JSONRPC2Session(serverURL);
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request("storeFile", requestID);
        String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));
        Map<String, Object> myParams = new HashMap<>();
        myParams.put("fileContent", fileContent);
        myParams.put("fileName", new File(filePath).getName());
        myParams.put("fileHashcode", fileContent.hashCode());
        request.setNamedParams(myParams);
        JSONRPC2Response response = null;
        try {
            response = pSession.send(request);
        } catch (JSONRPC2SessionException ignored) {
            System.out.println("File couldn't be stored at the peer.");
        }
        if (response != null && response.indicatesSuccess()) {
            System.out.println("File stored successfully. Hashcode: " + response.getResult());
        }
    }

    //make a retrive file request to Peer.
    public void retrieveFile(String fileHashcode) {
        //fileHashcode
        int hashcode = Integer.parseInt(fileHashcode) % 16;
        HashMap<String, String> peerToRetrieveFileFromMap = getClosestPeerInfo(hashcode);
        String peerID = peerToRetrieveFileFromMap.get("peerID");
        String peerIPAddress = peerToRetrieveFileFromMap.get("peerIPAddress");
        URL serverURL = null;
        try {
            serverURL = new URL("http://" + peerIPAddress + ":" + 6969);

        } catch (MalformedURLException ignored) {
        }
        JSONRPC2Session pSession = new JSONRPC2Session(serverURL);
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request("retreiveFile", requestID);
        Map<String, Object> myParams = new HashMap<>();
        myParams.put("fileHashcode", Integer.parseInt(fileHashcode));
        request.setNamedParams(myParams);
        JSONRPC2Response response = null;
        try {
            response = pSession.send(request);
        } catch (JSONRPC2SessionException ignored) {
            System.out.println("File couldn't be stored at the peer.");
        }
        if (response != null && response.indicatesSuccess()) {

            Gson gson = new Gson();
            Type mapType = new TypeToken<HashMap<String, String>>() {
            }.getType();
            HashMap<String, String> fileInfo = gson.fromJson(response.getResult().toString(), mapType);
            String fileContent = fileInfo.get("fileContent");
            String fileName = fileInfo.get("fileName");
            try (PrintWriter out = new PrintWriter("/home/rishabh/" + fileName)) {
                out.println(fileContent);
                System.out.println(fileName + " retrieved successfully");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String[] args) throws IOException {
        Client aClient = new Client();

        Scanner sc = new Scanner(System.in);
        requestID = new Random().nextInt(5000);

        while (true) {
            System.out.println("==================================================");
            System.out.println("\n 1. Store a File \n 2. " +
                    "Retrieve a File. \n");
            System.out.println("===================================================");
            String userChoice = sc.nextLine();
            while (!userChoice.equals("1") && !userChoice.equals("2")) {
                System.out.println("Please enter 1 to Store / 2 to Retrieve /");
                userChoice = sc.nextLine();
            }
            switch (userChoice) {
                case "1":
                    System.out.println("Please enter the path of file you want to store.");
                    String filePath = sc.nextLine();
                    while (filePath.isEmpty()) {
                        System.out.println("File Path cannot be empty. Please enter again.");
                        filePath = sc.nextLine();
                    }
                    int hashcode = aClient.getHashcode(filePath);
                    HashMap<String, String> peerNND = aClient.getClosestPeerInfo(hashcode);
                    aClient.storeFile(peerNND, filePath);
                    break;
                case "2":
                    System.out.println("Please enter the hashcode of the file you want to retrieve.");
                    String fileHashcode = sc.nextLine();
                    while (fileHashcode.isEmpty()) {
                        System.out.println("Hashcode cannot be empty. Please enter again.");
                        fileHashcode = sc.nextLine();
                    }
                    aClient.retrieveFile(fileHashcode);
                    break;
                default:
                    System.exit(0);
            }
        }
    }
}
