/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package frb.cosmos.proxy;

import java.io.IOException;
import java.net.ServerSocket;

/**
 *
 * @author frb
 */
public final class Proxy {
    
    private static final int PUBLIC_PORT = 14000;
    private static final int PRIVATE_PORT = 41000;
    private static final String PRIVATE_HOST = "0.0.0.0";
    
    /**
     * Constructor. It is private since utility classes should not have a public or default constructor.
     */
    private Proxy() {
    } // Proxy
    
    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;
        boolean listening = true;

        try {
            serverSocket = new ServerSocket(PUBLIC_PORT);
            System.out.println("Proxy started on: " + PUBLIC_PORT);
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + PUBLIC_PORT);
            System.exit(-1);
        } // try catch

        while (listening) {
            new ProxyThread(serverSocket.accept(), PUBLIC_PORT, PRIVATE_PORT, PRIVATE_HOST).start();
        } // while
        
        serverSocket.close();
    } // main
    
} // Proxy
