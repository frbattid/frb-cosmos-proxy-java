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
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

/**
 *
 * @author frb
 */
public class ProxyThread extends Thread {
    private Socket socket = null;
    private final int publicPort;
    private final int privatePort;
    private static final int BUFFER_SIZE = 32768;
    
    /**
     * Constructor.
     * @param socket
     * @param publicPort
     * @param privatePort
     */
    public ProxyThread(Socket socket, int publicPort, int privatePort) {
        super("ProxyThread");
        this.socket = socket;
        this.publicPort = publicPort;
        this.privatePort = privatePort;
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
        String method;
        String publicUrl = null;

        try {
            while ((inputLine = publicIn.readLine()) != null) {
                String[] tokens = inputLine.split(" ");
                method = tokens[0];
                publicUrl = tokens[1];
                System.out.println("Request received, method: " + method + ", URL: " + publicUrl);
            } // while
        } catch (Exception e) {
            return;
        } // try catch
        
        if (publicUrl == null) {
            return;
        } // if
        
        // change ports
        String privateUrl = publicUrl.replace(Integer.toString(publicPort), Integer.toString(privatePort));
        
        // forward the received request to the private port
        BufferedReader privateIn;

        try {
            URL url = new URL(privateUrl);
            URLConnection conn = url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(false);
            InputStream is;

            if (conn.getContentLength() > 0) {
                try {
                    is = conn.getInputStream();
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
