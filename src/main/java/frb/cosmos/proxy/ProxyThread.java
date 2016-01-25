/*
 * To change this license header, choose License Headers publicIn Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template publicIn the editor.
 */
package frb.cosmos.proxy;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

/**
 *
 * @author frb
 */
public class ProxyThread extends Thread {
    private Socket socket = null;
    private final int publicPort;
    private final int privatePort;
    private final String privateHost;
    private static final int BUFFER_SIZE = 32768;
    
    /**
     * Constructor.
     * @param socket
     * @param publicPort
     * @param privatePort
     */
    public ProxyThread(Socket socket, int publicPort, int privatePort, String privateHost) {
        super("ProxyThread");
        this.socket = socket;
        this.publicPort = publicPort;
        this.privatePort = privatePort;
        this.privateHost = privateHost;
    } // ProxyThread

    @Override
    public void run() {
        // open in and out regarding the public side
        DataOutputStream publicOut;
        BufferedReader publicIn;

        try {
            publicOut = new DataOutputStream(socket.getOutputStream());
            publicIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception e) {
            return;
        } // try catch

        // read the received request
        String inputLine;
        String method = null;
        String path = null;
        HashMap<String, String> publicHeaders = new HashMap<>();

        try {
            while ((inputLine = publicIn.readLine()) != null) {
                if (inputLine.length() > 0) {
                    String[] tokens = inputLine.split(" ");
                    String firstToken = tokens[0];
                    String secondToken = tokens[1];

                    if (firstToken.equals("GET")) {
                        method = firstToken;
                        path = secondToken;
                    } else {
                        publicHeaders.put(firstToken, secondToken);
                    } // if else

                    System.out.println(">> " + inputLine);
                } else {
                    break;
                } // if else
            } // while
        } catch (Exception e) {
            return;
        } // try catch
        
        if (method == null) {
            return;
        } // if
        
        // build the private headers
        HashMap<String, String> privateHeaders = new HashMap<>();
        
        for (String header : publicHeaders.keySet()) {
            if (header.equals("Host:")) {
                privateHeaders.put(header, privateHost + ":" + privatePort);
            } else {
                privateHeaders.put(header, publicHeaders.get(header));
            } // else
        } // for
        
        // build the private URL
        String privateURL = "http://" + privateHeaders.get("Host:") + path;
                
        // forward the received request to the private port
        BufferedReader privateIn;

        try {
            URL url = new URL(privateURL);
            URLConnection conn = url.openConnection();
            HttpURLConnection huc = (HttpURLConnection) conn;
            huc.setDoInput(true);
            huc.setDoOutput(false);
            
            for (String header : publicHeaders.keySet()) {
                String value = publicHeaders.get(header);
                huc.setRequestProperty(header, value);
                System.out.println(">> >> " + header + " " + value);
            } // for
            
            InputStream is;

            if (huc.getContentLength() > 0) {
                try {
                    is = huc.getInputStream();
                    privateIn = new BufferedReader(new InputStreamReader(is));
                } catch (IOException e) {
                    return;
                } // try catch
            } else {
                return;
            } // if else

            byte[] by = new byte[BUFFER_SIZE];
            int index = is.read(by, 0, BUFFER_SIZE);

            while (index != -1) {
                publicOut.write(by, 0, index);
                index = is.read(by, 0, BUFFER_SIZE);
            } // while
            
            publicOut.flush();
        } catch (Exception e) {
            //publicOut.writeBytes("");
            return;
        } // try catch

        // close everything
        try {
            privateIn.close();
            publicOut.close();
            publicIn.close();

            if (socket != null) {
                socket.close();
            } // if
        } catch (Exception e) {
        } // try catch

    } // run
    
} // ProxyThread
