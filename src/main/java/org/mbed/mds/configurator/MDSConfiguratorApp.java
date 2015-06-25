package org.mbed.mds.configurator;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/**
 * mDS Configurator
 */
public class MDSConfiguratorApp 
{
    static class MDSConfigurator implements HttpHandler {   
        private static int m_num_tables = 4;                                  // max number of tables shown
        
        private static String m_div_hider_tag = "__HIDE_TABLE_";              // DIV hiding table tag
        private static String m_div_hide = "div#__NAME__ { display: none; }"; // DIV hide directive template
        
        private static String m_title = "mbed Device Server Configuration";   // Title
        public  static int    m_port = 8234;                                  // default port we listen on... TCP
        
        private static String m_scripts_root = "./scripts/";                  // directory relative to jar file for scripts...
        private static String m_config_files_root = "/conf/";                 // directory relative to jar file for config files...
        private static String m_templates_root = "/templates/";               // directory relative to jar file for html templates...
        
        private static int m_extra_cred_slots = 2;                            // number of extra slots to insert into UI for new creds
        private static String m_empty_slot_key = "unused";                    // empty slot key
        private static String m_empty_slot_value = "unused";                  // empty slot value
        
        private final Properties m_mds_config_properties;
        private final Properties m_mds_creds_properties;
        private final Properties m_http_coap_media_types_properties;
        private final Properties m_logging_properties;
        private final Properties m_mqtt_gw_properties;
        
        private final Properties m_mds_config_properties_updated;
        
        public MDSConfigurator() {
            super();
            this.m_mds_config_properties = new Properties();
            this.m_mds_creds_properties = new Properties();
            this.m_http_coap_media_types_properties = new Properties();
            this.m_logging_properties = new Properties();
            this.m_mqtt_gw_properties = new Properties();
            this.m_mds_config_properties_updated = new Properties();
        }
        
        private String fileToString(String filename)  {
            String contents = "";
            InputStream input = null;
            
            try {
                String current = new java.io.File( "." ).getCanonicalPath();
                String fq_filename = current + this.m_templates_root + filename;
                input = new FileInputStream(fq_filename);
                Reader reader = new BufferedReader(new InputStreamReader(input));
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[8192];
                int read = 0;
                while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                    builder.append(buffer, 0, read);
                }
                input.close();
                return builder.toString();
            }
            catch (Exception ex) {
            }
            return null;
        }
        
        private String initializeResponse(String html) {
            // initialize the table with the CSS first
            html += this.fileToString("css.html");
            
            // add scripts
            html += this.fileToString("scripts.html");
            
            // add the table template
            html += this.fileToString("editor.html");
            
            // update some of the key variables
            String dir = this.getWorkingDirectory();
            html = html.replace("__TITLE__",this.m_title);
            
            // return the html
            return html;
        }
                
        private String getWorkingDirectory() {
            try {
                return new java.io.File( "." ).getCanonicalPath();
            }
            catch (Exception ex) {
            }
            return "./";
        }
        
        // open the mDS properties
        private Properties getProperties(Properties prop,String filename) {
            try {
                String fq_filename = this.getWorkingDirectory() + this.m_config_files_root + filename;
                //System.out.println("Opening File: " + fq_filename);
                InputStream input = new FileInputStream(fq_filename);
                prop.clear();
                prop.load(input);
                input.close();
            }
            catch (Exception ex) {
                System.out.println("Exception during Reading Properties File: " + ex.getMessage());
            }
            return prop;
        }
        
        // build out the properties table
        private String createConfigTableAsHTML(Properties props,String file, boolean editable_key) {
            // start the table
            String table = "<table border=\"0\">";
            
            // enumerate through the properties and fill the table
            Enumeration e = props.propertyNames();
            while (e.hasMoreElements()) {
                table += "<tr>";
                
                // Key
                String key = (String) e.nextElement();
                if (editable_key)
                    table += "<td id=\"" + key + "-key\" contenteditable=\"true\">" + key + "</td>";
                else
                    table += "<td id=\"" + key + "-key\" contenteditable=\"false\">" + key + "</td>";
                
                // Value
                String value = props.getProperty(key);               
                table += "<td id=\"" + key + "\" contenteditable=\"true\" align=\"left\" height=\"" + "auto" + "\" width=\"" + "auto" + "\">" + value + "</td>";
                String save_button = "<button name=\"save_button\" value=\"" + key + "\" type=\"button\" onclick=saveData('" + key + "','" + file + "') style=\"height:35px;width:80px\">SAVE</button>";
                table += "<td align=\"center\" height=\"35px\" width=\"210px\">" + save_button + "</td>";
     
                // finish row
                table += "</tr>";
            }
            
            // add the trailing tag
            table += "</table>";
                        
            // return the table
            return table;
        }
        
        private String buildConfigurationTable(String html,Properties props,String file,String key) { 
            return this.buildConfigurationTable(html, props, file, key, false);
        }
        
        private String buildConfigurationTable(String html,Properties props,String file,String key,boolean editable_key) {            
            // create the actual configuration table has HTML
            String preference_table = this.createConfigTableAsHTML(props,file,editable_key);
            
            // fill in the body
            html = html.replace(key,preference_table);
            
            // return the table
            return html;
        }
        
        public Map<String, String> queryToMap(String query){
            Map<String, String> result = new HashMap<String, String>();
            if (query != null && query.length() > 0) {
                for (String param : query.split("&")) {
                    String pair[] = param.split("=");
                    if (pair.length>1) {
                        result.put(pair[0], pair[1]);
                    }else{
                        result.put(pair[0], "");
                    }
                }
            }
            return result;
        }
                
        private String displayDeviceServerProperties(String html) {
            if (this.m_mds_config_properties.isEmpty()) this.getProperties(this.m_mds_config_properties,"deviceserver.properties");
            return this.buildConfigurationTable(html,this.m_mds_config_properties,"deviceserver.properties","__DS_CONFIG_TABLE__");
        }
        
        private void addEmptyCredentialSlots() {
            for(int i=0;i<this.m_extra_cred_slots;++i) {
                this.m_mds_creds_properties.put(this.m_empty_slot_key + "-" + (i+1),this.m_empty_slot_value);
            }
        }
        
        private String displayDeviceServerCredentials(String html) {
            if (this.m_mds_creds_properties.isEmpty()) {
                this.getProperties(this.m_mds_creds_properties,"credentials.properties");
                this.addEmptyCredentialSlots();
            }
            return this.buildConfigurationTable(html,this.m_mds_creds_properties,"credentials.properties","__DS_CREDS_TABLE__",true);
        }
        
        private String displayCoAPMediaTypesConfig(String html) {
            if (this.m_http_coap_media_types_properties.isEmpty()) this.getProperties(this.m_http_coap_media_types_properties,"http-coap-mediatypes.properties");
            return this.buildConfigurationTable(html,this.m_http_coap_media_types_properties,"http-coap-mediatypes.properties","__COAP_MEDIA_TYPE_TABLE__");
        }
        
        private String displayLoggingConfig(String html) {
            if (this.m_logging_properties.isEmpty()) this.getProperties(this.m_logging_properties,"log4j.properties");
            return this.buildConfigurationTable(html,this.m_logging_properties,"log4j.properties","__LOGGING_CONFIG_TABLE__");
        }
        
        private String displayMQTTGWConfig(String html) {
            if (this.m_mqtt_gw_properties.isEmpty()) this.getProperties(this.m_mqtt_gw_properties,"gateway.properties");
            return this.buildConfigurationTable(html,this.m_mqtt_gw_properties,"gateway.properties","__MQTT_GW_CONFIG_TABLE__");
        }
        
        // update mDS configuration
        private void updateDeviceServerConfiguration(String key,String value,String file) {
            // DEBUG
            System.out.println("mDS Configuration: Updating " + key + " = " + value);
            
            // save the updated value the preferences
            this.m_mds_config_properties_updated.put(key, value);
            this.m_mds_config_properties.put(key, value);
            
            // save the file...
            this.saveDeviceServerConfigurationFile();
        }
        
        // clear out empty slots
        private void clearEmptyCredentialSlots() {
            Enumeration e = this.m_mds_creds_properties.propertyNames();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                if (key.contains(this.m_empty_slot_key)) {
                    this.m_mds_creds_properties.remove(key);
                }
            }
        }
        
        // update mDS credentials
        private void updateDeviceServerCredentials(String key,String value,String file, String new_key) {
            if (new_key != null && new_key.equals(key) == false) {
                // DEBUG
                System.out.println("mDS Credentials(new key): Setting " + new_key + " = " + value);
                
                // delete the old preference
                this.m_mds_creds_properties.remove(key);

                // put a new key with the updated value
                this.m_mds_creds_properties.put(new_key, value);
            }
            else {
                // DEBUG
                System.out.println("mDS Credentials: Updating " + key + " = " + value);

                // save the updated value the preferences
                this.m_mds_creds_properties.put(key, value);
            }
            
            // clear out the empty slots
            this.clearEmptyCredentialSlots();

            // save the file...
            this.saveDeviceServerCredentialsFile();
            
            // put back the empty slots
            this.addEmptyCredentialSlots();
        }
        
        // update HTTP CoAP Media Types
        private void updateHTTPCoAPMediaTypes(String key,String value,String file) {            
            // DEBUG
            System.out.println("HTTP CoAP Media Types: Updating " + key + " = " + value);

            // save the updated value the preferences
            this.m_http_coap_media_types_properties.put(key, value);
            
            // save the file...
            this.saveHTTPCoAPMediaTypesConfigFile();
        }
        
        // update Logging Configuration
        private void updateLoggingConfiguration(String key,String value,String file) {
            // DEBUG
            System.out.println("Logging Configuration: Updating " + key + " = " + value);

            // save the updated value the preferences
            this.m_logging_properties.put(key, value);
            
            // save the file
            this.saveLoggingConfigFile();
        }
        
        // update MQTT GW Configuration
        private void updateMQTTGWConfiguration(String key,String value,String file) {
            // DEBUG
            System.out.println("MQTT GW Configuration: Updating " + key + " = " + value);

            // save the updated value the preferences
            this.m_mqtt_gw_properties.put(key, value);
            
            // save the file
            this.saveMQTTGWConfigFile();
        }
        
        private void executeScript(String script) {
            try {
                //System.out.println("Executing: " + this.m_scripts_root + script);
                Runtime.getRuntime().exec(this.m_scripts_root + script);
            } catch (Exception ex) {
                System.out.println("Exception caught: " + ex.getMessage() + " script: " + script);
            }
        }
        
        private boolean writePropertiesFile(Properties props,String filename) {
            return this.writePropertiesFile(null, props, filename);
        }
        
        private boolean writePropertiesFile(String comments,Properties props,String filename) {
            OutputStream output = null;
            boolean written = false;
                
            if (props.isEmpty() == false) {
                try {
                    String fq_filename = this.getWorkingDirectory() + this.m_config_files_root + filename;
                    output = new FileOutputStream(fq_filename);
                    props.store(output, comments);
                    written = true;
                } 
                catch (Exception ex) {
                    System.out.println("Exception caught: " + ex.getMessage() + " Filename: " + filename);
                }
            }
            else {
                System.out.println("No properties/changes to write out to filename: " + filename);
            }
            return written;
        }
        
        private void saveDeviceServerConfigurationFile() {
            // DEBUG
            System.out.println("Saving mDS Configuration File...");
            
            // write a new file out...
            boolean written = this.writePropertiesFile("mDS Configuration Updates",this.m_mds_config_properties_updated, "deviceserver.properties.updated");
            
            // execute the recombination script to merge with the default config file...
            if (written) this.executeScript("mergeDeviceServerConfiguration.sh");
        }
        
        private void saveDeviceServerCredentialsFile() {
            // DEBUG
            System.out.println("Saving mDS Credentials File...");
            
            // rewrite the file
            this.writePropertiesFile("mDS Credentials Updates",this.m_mds_creds_properties, "credentials.properties");
        }
        
        private void saveHTTPCoAPMediaTypesConfigFile() {
            // DEBUG
            System.out.println("Saving HTTP CoAP Media Types Config File...");
            
            // rewrite the file
            this.writePropertiesFile("HTTP CoAP Media Type Updates",this.m_http_coap_media_types_properties, "http-coap-mediatypes.properties");
        }
        
        private void saveLoggingConfigFile() {
            // DEBUG
            System.out.println("Saving Logging Config File...");
            
            // rewrite the file
            this.writePropertiesFile("Logging Updates",this.m_logging_properties, "log4j.properties");
        }
        
        private void saveMQTTGWConfigFile() {
            // DEBUG
            System.out.println("Saving MQTT GW Config File...");
            
            // rewrite the file
            this.writePropertiesFile("MQTT GW Updates",this.m_mqtt_gw_properties, "gateway.properties");
        }
        
        private String checkAndHideTable(String html,String div_name,String table_index,Properties table_properties) {
            // build out the TAG
            String tag = this.m_div_hider_tag + table_index + "__";
            
            // see if we have properties
            if (table_properties.isEmpty()) {
                // hide the table via DIV...
                String div = this.m_div_hide.replace("__NAME__", div_name);
                html = html.replace(tag, div);
            }
            else {
                // show the table... 
                html = html.replace(tag,"");
            }
            
            return html;
        }
        
        private String hideEmptyTables(String html) {
            html = this.checkAndHideTable(html,"ds_config_table","1",this.m_mds_config_properties);
            html = this.checkAndHideTable(html,"ds_creds_table","2",this.m_mds_creds_properties);
            html = this.checkAndHideTable(html,"http_coap_media_table","3",this.m_http_coap_media_types_properties);
            html = this.checkAndHideTable(html,"logging_table","4",this.m_logging_properties);
            html = this.checkAndHideTable(html,"mqtt_gw_config_table","5",this.m_mqtt_gw_properties);
            return html;
        }
        
        @Override
        public void handle(HttpExchange t) throws IOException {
            String html = "";
            
            // convert the query string...
            Map<String,String> query = this.queryToMap(t.getRequestURI().getQuery());
            
            // update individual settings for a given configuration...
            if (query.get("updated_key") != null) {
                String file = query.get("file"); 
                
                // mDS Configuration
                if (file.equalsIgnoreCase("deviceserver.properties")) {
                    this.updateDeviceServerConfiguration(query.get("updated_key"), query.get("updated_value"), file);
                }
                
                // mDS Credentials
                if (file.equalsIgnoreCase("credentials.properties")) {
                    this.updateDeviceServerCredentials(query.get("updated_key"), query.get("updated_value"), file, query.get("new_key"));
                }
                
                // HTTP CoAP Media Types
                if (file.equalsIgnoreCase("http-coap-mediatypes.properties")) {
                    this.updateHTTPCoAPMediaTypes(query.get("updated_key"), query.get("updated_value"), file);
                }
                
                // Logging Configuration
                if (file.equalsIgnoreCase("log4j.properties")) {
                    this.updateLoggingConfiguration(query.get("updated_key"), query.get("updated_value"), file);
                }
                
                // MQTT Gateway Configuration
                if (file.equalsIgnoreCase("gateway.properties")) {
                    this.updateMQTTGWConfiguration(query.get("updated_key"), query.get("updated_value"), file);
                }
            }
            
            // restart mDS
            if (query.get("mds") != null) {
                System.out.println("Restarting mbed Device Server...");
                this.executeScript("restartMDS.sh");
            }
            
            // restart MQTT GW
            if (query.get("mqttgw") != null) {
                System.out.println("Restarting mbed MQTT Gateway...");
                this.executeScript("restartMQTTGW.sh");
            }
            
            // initialize the response
            html = this.initializeResponse(html);

            // build out the display response
            html = this.displayDeviceServerProperties(html);
            html = this.displayDeviceServerCredentials(html);
            html = this.displayCoAPMediaTypesConfig(html);
            html = this.displayLoggingConfig(html);
            html = this.displayMQTTGWConfig(html);
            
            // update DIV's for tables that need to be hidden
            html = this.hideEmptyTables(html);
            
            // send the response and close
            t.sendResponseHeaders(200,html.length());
            OutputStream os = t.getResponseBody();
            os.write(html.getBytes());
            
            // clean up
            os.close();
        }
    }
    
    public static void main(String[] args) throws Exception {
        // initialize and load the preferences for MDSConfigurator...
        
        try {
            // Create the HTTPS Server and SSL/TLS Context
            HttpsServer server = HttpsServer.create(new InetSocketAddress(MDSConfigurator.m_port),0);
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = "arm1234".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream("mdsconfigurator.jks");
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
                            catch ( Exception ex ) {
                                System.out.println("HttpsConfigurator: Failed to create HTTPS port. Exception: " + ex.getMessage());
                            }
                        }
                    } );
                        
            // create the main context
            HttpContext context = server.createContext("/", new MDSConfigurator());
            
            // Create a basic auth authenticator context
            context.setAuthenticator(new BasicAuthenticator("get") {
                public boolean checkCredentials(String user, String pwd) {
                    return user.equals("admin") && pwd.equals("admin");
                }
            });
            
            // no executor
            server.setExecutor(null);
            
            // start the service
            server.start();
        }
        catch (Exception ex) {
            System.out.println("Caught Exception in main(): Exception: " + ex.getMessage());
        }
    }
}

