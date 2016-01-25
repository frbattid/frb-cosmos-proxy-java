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
    private enum HttpMethod { GET, POST, PUT, DELETE }
    
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
        boolean isFirstLine = true;
        boolean readingPayload = false;
        int payloadLength = 0;
        HttpMethod method = null;
        String path = null;
        HashMap<String, String> publicHeaders = new HashMap<>();
        String payload = "";
        
        try {
            while ((inputLine = publicIn.readLine()) != null) {
                if (inputLine.length() > 0) {
                    System.out.println(">> " + inputLine);
                    String[] tokens = inputLine.split(" ");
                    
                    if (isFirstLine) {
                        method = HttpMethod.valueOf(tokens[0]);
                        path = tokens[1];
                        isFirstLine = false;
                    } else if (!readingPayload) {
                        String headerName = tokens[0].replaceFirst(":", "");
                        String headerValue = tokens[1];
                        publicHeaders.put(headerName, headerValue);
                        
                        if (headerName.equals("Content-Length")) {
                            payloadLength = Integer.parseInt(headerValue);
                        } // if
                    } else {
                        if (payload.isEmpty()) {
                            payload = inputLine;
                        } else {
                            payload += inputLine;
                        } // if else
                        
                        if (payload.length() == payloadLength) {
                            readingPayload = false;
                        } // if
                    } // else
                } else if (!readingPayload && payloadLength > 0) {
                    readingPayload = true;
                } else {
                    break;
                } // if else
            } // while
        } catch (IOException | NumberFormatException e) {
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
        String response = forward(method, privateURL, privateHeaders, payload);
        
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
    
    private String forward(HttpMethod method, String privateURL, HashMap<String, String> headers, String payload) {
        String response = "";
        BufferedReader privateIn;

        try {
            URL url = new URL(privateURL);
            System.out.println(">> >> " + method.toString() + " " + url.getPath() + " HTTP/1.1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            switch (method) {
                case GET:
                    conn.setDoInput(true);
                    conn.setDoOutput(false);
                    conn.setRequestMethod("GET");
                    break;
                case POST:
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");
                    break;
                case PUT:
                    conn.setDoOutput(true);
                    conn.setRequestMethod("PUT");
                    break;
                case DELETE:
                    conn.setDoOutput(true);
                    conn.setRequestMethod("DELETE");
                    break;
                default:
                    break;
            } // switch
            
            for (String header : headers.keySet()) {
                String value = headers.get(header);
                conn.setRequestProperty(header, value);
                System.out.println(">> >> " + header + ": " + value);
            } // for
            
            switch (method) {
                case POST:
                case PUT:
                    PrintWriter privateOut = new PrintWriter(conn.getOutputStream());
                    privateOut.write(payload);
                    privateOut.close();
                    System.out.println(">> >> " + payload);
                    break;
                case GET:
                case DELETE:
                default:
                    break;
            } // switch
            
            privateIn = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            
            // get the response status
            response = "HTTP/1.1 " + conn.getResponseCode() + " " + conn.getResponseMessage() + "\r\n";
            System.out.println("<< << HTTP/1.1 " + conn.getResponseCode() + " " + conn.getResponseMessage());
            
            // get the response headers
            boolean isChunked = false;
            
            for (Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
                if (header.getKey() != null) {
                    String name = header.getKey();
                    String value = header.getValue().toString().replaceAll("[\\[\\]]", "");
                    System.out.println("<< << " + header.getKey() + ": " + value);
                    response += name + ": " + value + "\r\n";
                    
                    if (name.equals("Transfer-Encoding") && value.equals("Chunked")) {
                        isChunked = true;
                    } // if
                } // if
            } // for
            
            response += "\r\n";
            
            // get the response payload
            String responsePayload = "";
            String line;
            
            while ((line = privateIn.readLine()) != null) {
                responsePayload += line;
            } // while
            
            privateIn.close();
            
            // compose the final response
            if (isChunked) {
                response += Integer.valueOf(String.valueOf(responsePayload.length()), 16) + "\r\n";
            } // if
            
            response += responsePayload + "\r\n";
            return response;
        } catch (IOException | NumberFormatException e) {
            System.out.println("Error while forwarding the request: " + e.getMessage());
            return response;
        } // try catch
    } // forward
    
} // ProxyThread
