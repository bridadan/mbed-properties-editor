package com.arm.mbed.properties.editor;

import com.sun.net.httpserver.HttpContext;
import java.io.IOException;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.FileInputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import com.arm.mbed.properties.editor.processor.PropertiesEditorProcessor;
import java.net.InetSocketAddress;

/**
 * Main - this application will read and permit editing of java properties files
 * @author Doug Anson
 */
public class Main 
{
    // our own configuration properties file (must be fully qualified)
    public static final String PROPERTIES_EDITOR_DEFAULT_CONFIG = "/home/arm/properties-editor/conf/properties-editor.properties";
    
    /**
     * Primary entry point for properties-editor Application
     * @param args - command line arguments
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        // initialize and load the properties file...
        PropertiesEditorProcessor processor = new PropertiesEditorProcessor(false);
        
        // load the properties up in the processor
        processor.loadProperties(PROPERTIES_EDITOR_DEFAULT_CONFIG);
        
        try {
            // Create the HTTPS Server and SSL/TLS Context
            HttpsServer server = HttpsServer.create(new InetSocketAddress(processor.getIntProperty("default_port")),0);
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = processor.getProperty("keystore_password").toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream(processor.getProperty("keystore"));
            ks.load(fis,password);

            // setup the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks,password);

            // setup the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            // setup the HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(),tmf.getTrustManagers(),null);
            server.setHttpsConfigurator( 
                new HttpsConfigurator(sslContext)
                {
                    @Override
                    public void configure(HttpsParameters params) {
                        try {
                            // initialise the SSL context
                            SSLContext c = SSLContext.getDefault ();
                            SSLEngine engine = c.createSSLEngine ();
                            params.setNeedClientAuth ( false );
                            params.setCipherSuites ( engine.getEnabledCipherSuites () );
                            params.setProtocols ( engine.getEnabledProtocols () );

                            // get the default parameters
                            SSLParameters defaultSSLParameters = c.getDefaultSSLParameters ();
                            params.setSSLParameters ( defaultSSLParameters );
                        }
                        catch ( NoSuchAlgorithmException ex ) {
                            System.out.println("Main: Failed to create HTTPS port. Exception: " + ex.getMessage());
                        }
                    }
                } );
                        
            // create the main context
            HttpContext context = server.createContext("/", processor);
            
            // Create a basic auth authenticator context
            context.setAuthenticator(processor);
            
            // no executor
            server.setExecutor(null);
            
            // start the service
            server.start();
        }
        catch (IOException ex) {
            System.out.println("Caught Exception in main(): Exception: " + ex.getMessage());
        }
        catch (KeyManagementException ex) {
            System.out.println("Caught Exception in main(): Exception: " + ex.getMessage());
        }
        catch (KeyStoreException ex) {
            System.out.println("Caught Exception in main(): Exception: " + ex.getMessage());
        }
        catch (NoSuchAlgorithmException ex) {
            System.out.println("Caught Exception in main(): Exception: " + ex.getMessage());
        }
        catch (UnrecoverableKeyException ex) {
            System.out.println("Caught Exception in main(): Exception: " + ex.getMessage());
        }
        catch (CertificateException ex) {
            System.out.println("Caught Exception in main(): Exception: " + ex.getMessage());
        }
    }
}