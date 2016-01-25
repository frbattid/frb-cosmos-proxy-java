/*
 * To change this license header, choose License Headers publicIn Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template publicIn the editor.
 */
package frb.cosmos.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 *
 * @author frb
 */
public class ProxyThread extends Thread {
    
    /**
     * Accepted Http methods.
     */
    private enum HttpMethod { GET, POST }
    
    private Socket socket = null;
    private final int privatePort;
    private final String privateHost;
    
    /**
     * Constructor.
     * @param socket
     * @param privatePort
     * @param privateHost
     */
    public ProxyThread(Socket socket, int privatePort, String privateHost) {
        super("ProxyThread");
        this.socket = socket;
        this.privatePort = privatePort;
        this.privateHost = privateHost;
    } // ProxyThread

    @Override
    public void run() {
        // open in and out regarding the public side
        PrintWriter publicOut;
        BufferedReader publicIn;

        try {
            publicOut = new PrintWriter(socket.getOutputStream());
            publicIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception e) {
            return;
        } // try catch

        // read the received request
        String inputLine;
        boolean firstLine = true;
        HttpMethod method = null;
        String path = null;
        HashMap<String, String> publicHeaders = new HashMap<>();

        try {
            while ((inputLine = publicIn.readLine()) != null) {
                if (inputLine.length() > 0) {
                    System.out.println(">> " + inputLine);
                    String[] tokens = inputLine.split(" ");
                    
                    if (firstLine) {
                        method = HttpMethod.valueOf(tokens[0]);
                        path = tokens[1];
                        firstLine = false;
                    } else {
                        publicHeaders.put(tokens[0].replaceFirst(":", ""), tokens[1]);
                    } // if else
                } else {
                    break;
                } // if else
            } // while
        } catch (Exception e) {
            System.out.println("Error while reading the received request: " + e.getMessage());
            return;
        } // try catch
        
        if (method == null || path == null) {
            System.out.println("Malformed request");
            return;
        } // if
        
        // build the private headers
        HashMap<String, String> privateHeaders = new HashMap<>();
        
        for (String header : publicHeaders.keySet()) {
            if (header.equals("Host")) {
                // this is a proxy for Cosmos, which needs this kind of translation
                privateHeaders.put(header, privateHost + ":" + privatePort);
            } else {
                privateHeaders.put(header, publicHeaders.get(header));
            } // else
        } // for
        
        // build the private URL
        String privateURL = "http://" + privateHeaders.get("Host") + path;
                
        // forward the received request to the private host:port
        String response;
        
        switch (method) {
            case GET:
                response = sendGET(method, privateURL, privateHeaders);
                break;
            case POST:
                response = sendPOST();
                break;
            default:
                response = "";
        } // switch
        
        // forward the response to the client
        publicOut.write(response);
        publicOut.flush();
        System.out.println("<< " + response);

        // close everything
        try {
            publicOut.close();
            publicIn.close();

            if (socket != null) {
                socket.close();
            } // if
        } catch (Exception e) {
            System.out.println("Error while closing the streams");
        } // try catch

    } // run
    
    private String sendGET(HttpMethod method, String privateURL, HashMap<String, String> headers) {
        String response = "";
        BufferedReader privateIn;

        try {
            URL url = new URL(privateURL);
            System.out.println(">> >> " + method.toString() + " " + url.getPath() + " HTTP/1.1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(false);
            
            for (String header : headers.keySet()) {
                String value = headers.get(header);
                conn.setRequestProperty(header, value);
                System.out.println(">> >> " + header + ": " + value);
            } // for

            try {
                privateIn = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } catch (IOException e) {
                return response;
            } // try catch
            
            // get the response status
            System.out.println("<< << " + conn.getResponseCode() + " " + conn.getResponseMessage());
            
            // get the response headers
            for (Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
                System.out.println("<< << " + header.getKey() + ": " + header.getValue());
            } // for
            
            // get the response payload
            String line;
            
            while ((line = privateIn.readLine()) != null) {
                if (response.isEmpty()) {
                    response = line;
                } else {
                    response += "\n" + line;
                } // if else
            } // while

            privateIn.close();
            
            if (!response.isEmpty()) {
                System.out.println("<< << " + response);
            } // if
            
            return response;
        } catch (Exception e) {
            return response;
        } // try catch // try catch
    } // sendGET
    
    private String sendPOST() {
        return "";
    } // sendPORT
    
} // ProxyThread
