package org.mbed.mds.configurator;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
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
 * MDSConfiguratorApp - this application displays configuration files from the mDS environment (as well as itself) to permit
 * web-based customization of mDS and its sub-components (including the optional MQTT Gateway)
 * 
 * @author Doug Anson
 */
public class MDSConfiguratorApp 
{
    /**
     * MDSConfigurator - the primary class implementing MDSConfiguratorApp
     */
    static class MDSConfigurator extends BasicAuthenticator implements HttpHandler {   
        private static final String m_title = "mbed Device Server Configuration";   // Title
        private static final int m_num_tables = 6;                                  // max number of tables shown
        
        private static final String m_div_hider_tag = "__HIDE_TABLE_";              // DIV hiding table tag
        private static final String m_div_hide = "div#__NAME__ { display: none; }"; // DIV hide directive template
        
        private static final String m_scripts_root = "./scripts/";                  // directory relative to jar file for scripts...
        private static final String m_config_files_root = "/conf/";                 // directory relative to jar file for config files...
        private static final String m_templates_root = "/templates/";               // directory relative to jar file for html templates...
        
        private static final int m_extra_slots = 5;                                 // number of extra slots to insert into UI for new config entries
        private static final String m_empty_slot_key = "unused";                    // empty slot key
        private static final String m_empty_slot_value = "unused";                  // empty slot value
        
        private final Properties m_mds_config_properties;                     // mDS Properties
        private final Properties m_mds_creds_properties;                      // mDS Credential Properties
        private final Properties m_http_coap_media_types_properties;          // HTTP CoAP Media Types Properties
        private final Properties m_logging_properties;                        // Logging Properties
        private final Properties m_mqtt_gw_properties;                        // MQTT Gateway Properties
        private final Properties m_mds_config_properties_updated;             // mDS Updated Properties (deltas)
        private final Properties m_configurator_properties;                   // mDSConfigurator App (self) properties
        
        /**
         * Default Constructor
         */
        public MDSConfigurator() {
            super("get");
            this.m_mds_config_properties = new Properties();
            this.m_mds_creds_properties = new Properties();
            this.m_http_coap_media_types_properties = new Properties();
            this.m_logging_properties = new Properties();
            this.m_mqtt_gw_properties = new Properties();
            this.m_mds_config_properties_updated = new Properties();
            this.m_configurator_properties = new Properties();
        }
        
        /**
         * read in file into a string
         */
        @SuppressWarnings("empty-statement")
        private String fileToString(String filename)  {
            String contents = "";
            InputStream input = null;
            
            try {
                String current = new java.io.File( "." ).getCanonicalPath();
                String fq_filename = current + MDSConfigurator.m_templates_root + filename;
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
            catch (IOException ex) {
                // silent
                ;
            }
            return null;
        }
        
        /**
         * initialize the HTTP response HTML content
         */
        private String initializeResponse(String html) {
            // initialize the table with the CSS first
            html += this.fileToString("css.html");
            
            // add scripts
            html += this.fileToString("scripts.html");
            
            // add the table templates/editor page
            html += this.fileToString("editor.html");
            
            // update some of the key variables
            html = html.replace("__TITLE__",MDSConfigurator.m_title);
            
            // return the html
            return html;
        }
        
        /**
         * Get the current working directory
         */
        @SuppressWarnings("empty-statement")
        private String getWorkingDirectory() {
            try {
                return new java.io.File(".").getCanonicalPath();
            }
            catch (IOException ex) {
                // silent
                ;
            }
            return "./";
        }
        
        /**
         * Open Properties and read in from properties file
         */
        @SuppressWarnings("empty-statement")
        private Properties getProperties(Properties prop,String filename) {
            InputStream input = null;
            try {
                String fq_filename = this.getWorkingDirectory() + MDSConfigurator.m_config_files_root + filename;
                //System.out.println("Opening File: " + fq_filename);
                input = new FileInputStream(fq_filename);
                prop.clear();
                prop.load(input);
                input.close();
            }
            catch (IOException ex) {
                System.out.println("Exception during Reading Properties File: " + ex.getMessage());
                prop.clear();
                try {
                    if (input != null) {
                        input.close();
                    }
                }
                catch (IOException ioex) {
                    // silent
                    ;
                }
            }
            return prop;
        }
            
        private boolean showField(Properties props,String key) {
            boolean show = false;
            if (props != null && props.isEmpty() == false && key != null && key.length() > 0) {
                String fields = props.getProperty("config_fields");
                if (fields != null && fields.length() > 0 && props != null) {
                    String[] shown = fields.split(";");
                    for(int i=0;i<shown.length && !show;++i) {
                        if (key.equalsIgnoreCase(shown[i])) {
                            show = true;
                        }
                    }
                }
            }
            return show;
        }
        
        /**
         * Build out the HTML table representing the properties file 
         */
        private String createConfigTableAsHTML(Properties props,String file, boolean editable_key,boolean do_filter) {
            // start the table
            String table = "<table border=\"0\">";
            boolean shown = true;
            
            // enumerate through the properties and fill the table
            Enumeration e = props.propertyNames();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                if (do_filter) shown = this.showField(props,key);
                if (shown) {
                    table += "<tr>";

                    // Key
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
            }
            
            // add the trailing tag
            table += "</table>";
                        
            // return the table
            return table;
        }
        
        /**
         * Build out the configuration table (properties from a properties file) as HTML content
         */
        private String buildConfigurationTable(String html,Properties props,String file,String key) { 
            return this.buildConfigurationTable(html, props, file, key, false, false);
        }
        
        /**
         * Build out the configuration table (properties from a properties file) as HTML content
         */
        private String buildConfigurationTable(String html,Properties props,String file,String key,boolean do_filter) { 
            return this.buildConfigurationTable(html, props, file, key, false,do_filter);
        }
        
        /**
         * Build out the configuration table (properties from a properties file) as HTML content
         */
        private String buildConfigurationTable(String html,Properties props,String file,String key,boolean editable_key,boolean do_filter) {            
            // create the actual configuration table has HTML
            String preference_table = this.createConfigTableAsHTML(props,file,editable_key,do_filter);
            
            // fill in the body
            html = html.replace(key,preference_table);
            
            // return the table
            return html;
        }
        
        /**
         * convert the QueryString to a Map<>
         */
        private Map<String, String> queryToMap(String query){
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
        
        /**
         * Display the device server properties as HTML
         */
        private String displayDeviceServerProperties(String html) {
            if (this.m_mds_config_properties.isEmpty()) this.getProperties(this.m_mds_config_properties,"deviceserver.properties");
            return this.buildConfigurationTable(html,this.m_mds_config_properties,"deviceserver.properties","__DS_CONFIG_TABLE__");
        }
        
        /**
         * Add some empty configuration slots in the configuration table for adding new config entries
         */
        private void addEmptyConfigSlots(Properties props) {
            for(int i=0;i<MDSConfigurator.m_extra_slots;++i) {
                Object put = props.put(MDSConfigurator.m_empty_slot_key + "-" + (i+1),MDSConfigurator.m_empty_slot_value);
            }
        }
        
        /**
         * Display the credential properties as HTML
         */
        private String displayDeviceServerCredentials(String html) {
            if (this.m_mds_creds_properties.isEmpty()) {
                this.getProperties(this.m_mds_creds_properties,"credentials.properties");
                // DISABLE: this.addEmptyConfigSlots(this.m_mds_creds_properties);
            }
            return this.buildConfigurationTable(html,this.m_mds_creds_properties,"credentials.properties","__DS_CREDS_TABLE__",true);
        }
        
        /**
         * Display the CoAP HTML Media Types properties as HTML
         */
        private String displayCoAPMediaTypesConfig(String html) {
            if (this.m_http_coap_media_types_properties.isEmpty()) this.getProperties(this.m_http_coap_media_types_properties,"http-coap-mediatypes.properties");
            return this.buildConfigurationTable(html,this.m_http_coap_media_types_properties,"http-coap-mediatypes.properties","__COAP_MEDIA_TYPE_TABLE__");
        }
        
        /**
         * Display the Logging File properties as HTML
         */
        private String displayLoggingConfig(String html) {
            if (this.m_logging_properties.isEmpty()) this.getProperties(this.m_logging_properties,"log4j.properties");
            return this.buildConfigurationTable(html,this.m_logging_properties,"log4j.properties","__LOGGING_CONFIG_TABLE__");
        }
        
        /**
         * Display the MQTT Gateway properties as HTML
         */
        private String displayMQTTGWConfig(String html) {
            if (this.m_mqtt_gw_properties.isEmpty()) {
                this.getProperties(this.m_mqtt_gw_properties,"gateway.properties");
                // DISABLE: this.addEmptyConfigSlots(this.m_mqtt_gw_properties);
            }
            return this.buildConfigurationTable(html,this.m_mqtt_gw_properties,"gateway.properties","__MQTT_GW_CONFIG_TABLE__",true,true);  // filter
        }
        
        /**
         * Display the mDSConfigurator App (self) properties as HTML
         */
        private String displayConfiguratorConfig(String html) {
            if (this.m_configurator_properties.isEmpty()) this.getProperties(this.m_configurator_properties,"configurator.properties");
            return this.buildConfigurationTable(html,this.m_configurator_properties,"configurator.properties","__CONFIGURATOR_CONFIG_TABLE__");
        }
        
        /**
         * Update the device server configuration (special handling since the default device server properties file is commented out)
         */
        private void updateDeviceServerConfiguration(String key,String value,String file) {
            // DEBUG
            System.out.println("mDS Configuration: Updating " + key + " = " + value);
            
            // save the updated value the preferences
            this.m_mds_config_properties_updated.put(key, value);
            this.m_mds_config_properties.put(key, value);
            
            // save the file...
            this.saveDeviceServerConfigurationFile();
        }
        
        /**
         * Clear out the non-used extra configuration entry slots - don't try to store them...
         */
        private void clearEmptyConfigSlots(Properties props) {
            Enumeration e = props.propertyNames();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                if (key.contains(MDSConfigurator.m_empty_slot_key)) {
                    props.remove(key);
                }
            }
        }
        
        /**
         * Update an expandable properties file
         */
        private void updateExpandableConfiguration(Properties props, String key,String value,String file, String new_key) {
            if (new_key != null && new_key.equals(key) == false) {
                // DEBUG
                System.out.println("mDS config(new key): Setting " + new_key + " = " + value);
                
                // delete the old preference
                props.remove(key);

                // put a new key with the updated value
                props.put(new_key, value);
            }
            else {
                // DEBUG
                System.out.println("mDS config: Updating " + key + " = " + value);

                // save the updated value the preferences
                props.put(key, value);
            }
        }
        
        /**
         * Update the credentials properties file
         */
        private void updateDeviceServerCredentials(String key,String value,String file, String new_key) {
            this.updateExpandableConfiguration(this.m_mds_creds_properties,key,value,file,new_key);
            
            // clear out the empty slots
            this.clearEmptyConfigSlots(this.m_mds_creds_properties);

            // save the file...
            this.saveDeviceServerCredentialsFile();
            
            // put back the empty config slots
            this.addEmptyConfigSlots(this.m_mds_creds_properties);
        }
        
        /**
         * Update the HTTP CoAP Media Types properties file
         */
        private void updateHTTPCoAPMediaTypes(String key,String value,String file) {            
            // DEBUG
            System.out.println("HTTP CoAP Media Types: Updating " + key + " = " + value);

            // save the updated value the preferences
            this.m_http_coap_media_types_properties.put(key, value);
            
            // save the file...
            this.saveHTTPCoAPMediaTypesConfigFile();
        }
        
        /**
         * Update the Logging properties file
         */
        private void updateLoggingConfiguration(String key,String value,String file) {
            // DEBUG
            System.out.println("Logging Configuration: Updating " + key + " = " + value);

            // save the updated value the preferences
            this.m_logging_properties.put(key, value);
            
            // save the file
            this.saveLoggingConfigFile();
        }
        
        /**
         * Update the MQTT Gateway properties file
         */
        private void updateMQTTGWConfiguration(String key,String value,String file,String new_key) {
           
            // DISABLE this.updateExpandableConfiguration(this.m_mqtt_gw_properties,key,value,file,new_key);
            
            // clear out the empty slots
            // DISABLE this.clearEmptyConfigSlots(this.m_mqtt_gw_properties);
            
            // DEBUG
            System.out.println("Logging MQTT GW Configuration: Updating " + key + " = " + value);
            
            // save the updated value in preferences
            this.m_mqtt_gw_properties.put(key, value);

            // save the file
            this.saveMQTTGWConfigFile();
            
            // put back the empty config slots
            // DISABLE this.addEmptyConfigSlots(this.m_mqtt_gw_properties);
        }
        
        /**
         * Update the mDSConfigurator App (self) properties file
         */
        private void updateConfiguratorConfiguration(String key,String value,String file) {
            // DEBUG
            System.out.println("Configurator Configuration: Updating " + key + " = " + value);

            // save the updated value the preferences
            this.m_configurator_properties.put(key, value);
            
            // save the file
            this.saveConfiguratorConfigFile();
        }
        
        /**
         * Execute a script
         */
        private void executeScript(String script) {
            try {
                //System.out.println("Executing: " + this.m_scripts_root + script);
                Runtime.getRuntime().exec(MDSConfigurator.m_scripts_root + script);
            } catch (IOException ex) {
                System.out.println("Exception caught: " + ex.getMessage() + " script: " + script);
            }
        }
        
        /**
         * Write a properties file
         */
        private boolean writePropertiesFile(Properties props,String filename) {
            return this.writePropertiesFile(null, props, filename);
        }
        
        /**
         * Write a properties file (with comments)
         */
        private boolean writePropertiesFile(String comments,Properties props,String filename) {
            OutputStream output = null;
            boolean written = false;
                
            if (props.isEmpty() == false) {
                try {
                    String fq_filename = this.getWorkingDirectory() + MDSConfigurator.m_config_files_root + filename;
                    output = new FileOutputStream(fq_filename);
                    props.store(output, comments);
                    written = true;
                } 
                catch (IOException ex) {
                    System.out.println("Exception caught: " + ex.getMessage() + " Filename: " + filename);
                }
            }
            else {
                System.out.println("No properties/changes to write out to filename: " + filename);
            }
            return written;
        }
        
        /**
         * Save the device server configuration file
         */
        private void saveDeviceServerConfigurationFile() {
            // DEBUG
            System.out.println("Saving mDS Configuration File...");
            
            // write a new file out...
            boolean written = this.writePropertiesFile("mDS Configuration Updates",this.m_mds_config_properties_updated, "deviceserver.properties.updated");
            
            // execute the recombination script to merge with the default config file...
            if (written) this.executeScript("mergeDeviceServerConfiguration.sh");
        }
        
        /**
         * Save the device server credentials configuration file
         */
        private void saveDeviceServerCredentialsFile() {
            // DEBUG
            System.out.println("Saving mDS Credentials File...");
            
            // rewrite the file
            this.writePropertiesFile("mDS Credentials Updates",this.m_mds_creds_properties, "credentials.properties");
        }
        
        /**
         * Save the HTML CoAP Media Types configuration file
         */
        private void saveHTTPCoAPMediaTypesConfigFile() {
            // DEBUG
            System.out.println("Saving HTTP CoAP Media Types Config File...");
            
            // rewrite the file
            this.writePropertiesFile("HTTP CoAP Media Type Updates",this.m_http_coap_media_types_properties, "http-coap-mediatypes.properties");
        }
        
        /**
         * Save the logging configuration file
         */
        private void saveLoggingConfigFile() {
            // DEBUG
            System.out.println("Saving Logging Config File...");
            
            // rewrite the file
            this.writePropertiesFile("Logging Updates",this.m_logging_properties, "log4j.properties");
        }
        
        /**
         * Save the MQTT Gateway configuration file
         */
        private void saveMQTTGWConfigFile() {
            // DEBUG
            System.out.println("Saving MQTT GW Config File...");
            
            // rewrite the file
            this.writePropertiesFile("MQTT GW Updates",this.m_mqtt_gw_properties, "gateway.properties");
        }
        
        /**
         * Save the mDSConfigurator App (self) configuration file
         */
        private void saveConfiguratorConfigFile() {
            // DEBUG
            System.out.println("Saving Configurator Config File...");
            
            // rewrite the file
            this.writePropertiesFile("ConfiguratorUpdates",this.m_configurator_properties, "configurator.properties");
        }
        
        /**
         * If the configuration table has no entries, hide it from display (using CCS primatives)
         */
        private String checkAndHideTable(String html,String div_name,String table_index,Properties table_properties) {
            // build out the TAG
            String tag = MDSConfigurator.m_div_hider_tag + table_index + "__";
            
            // see if we have properties
            if (table_properties.isEmpty()) {
                // hide the table via DIV...
                String div = MDSConfigurator.m_div_hide.replace("__NAME__", div_name);
                html = html.replace(tag, div);
            }
            else {
                // show the table... 
                html = html.replace(tag,"");
            }
            
            return html;
        }
        
        /**
         * Make sure that every table we display has something to display... if its empty, hide it...
         */
        private String hideEmptyTables(String html) {
            html = this.checkAndHideTable(html,"ds_config_table","1",this.m_mds_config_properties);
            html = this.checkAndHideTable(html,"ds_creds_table","2",this.m_mds_creds_properties);
            html = this.checkAndHideTable(html,"http_coap_media_table","3",this.m_http_coap_media_types_properties);
            html = this.checkAndHideTable(html,"logging_table","4",this.m_logging_properties);
            html = this.checkAndHideTable(html,"mqtt_gw_config_table","5",this.m_mqtt_gw_properties);
            html = this.checkAndHideTable(html,"configurator_config_table","6",this.m_configurator_properties);
            return html;
        }
        
        /**
         * Default HttpHandler handler method
         * @param  t - the HttpExchange instance
         * @throws IOException 
         */
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
                    this.updateMQTTGWConfiguration(query.get("updated_key"), query.get("updated_value"), file , query.get("new_key"));
                }
                
                // Configurator Configuration
                if (file.equalsIgnoreCase("configurator.properties")) {
                    this.updateConfiguratorConfiguration(query.get("updated_key"), query.get("updated_value"), file);
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
            html = this.displayConfiguratorConfig(html);
            
            // update DIV's for tables that need to be hidden
            html = this.hideEmptyTables(html);
            
            // send the response and close
            t.sendResponseHeaders(200,html.length());
            OutputStream os = t.getResponseBody();
            os.write(html.getBytes());
            
            // clean up
            os.close();
        }
        
        /**
         * Load up the mDSConfigurator App (self) properties file
         * @param filename - name of the mDSConfigurator properties file
         */
        public void loadProperties(String filename) {
            this.getProperties(this.m_configurator_properties, filename);
        }
        
        /**
         * Get a string-based property for mDSConfigurator App (self)
         * @param key - name of the property
         * @return - the property value if found, NULL otherwise
         */
        public String getProperty(String key) {
            return (String)this.m_configurator_properties.get(key);
        }
        
        /**
         * Get an integer-based property for mDSConfigurator App (self)
         * @param key - name of the property
         * @return - the property value if found as an integer, -1 otherwise or if parsing errors occur
         */
        public int getIntProperty(String key) {
            try {
                String s_value = this.getProperty(key);
                return Integer.parseInt(s_value);
            }
            catch (NumberFormatException ex) {
                System.out.println("Exception caught in getIntProperty (unable to parse integer): " + ex.getMessage());
            }
            return -1;
        }
        
        /**
         * Primary authenticator extending BasicAuthenticator's checkCredentials() method
         * @param user - input username
         * @param pwd - input secret
         * @return true - authenticated, false - otherwise
         */
        @Override
        public boolean checkCredentials(String user, String pwd) {
            return user.equals(getProperty("admin_username")) && pwd.equals(getProperty("admin_password"));
        }
    }
    
    /**
     * Primary entry point for mDSConfigurator App (self)
     * @param args - command line arguments
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        // initialize and load the preferences for MDSConfigurator...
        MDSConfigurator handler = new MDSConfigurator();
        
        // load the properties up
        handler.loadProperties("configurator.properties");
        
        try {
            // Create the HTTPS Server and SSL/TLS Context
            HttpsServer server = HttpsServer.create(new InetSocketAddress(handler.getIntProperty("default_port")),0);
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = handler.getProperty("keystore_password").toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream(handler.getProperty("keystore"));
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
                                System.out.println("HttpsConfigurator: Failed to create HTTPS port. Exception: " + ex.getMessage());
                            }
                        }
                    } );
                        
            // create the main context
            HttpContext context = server.createContext("/", handler);
            
            // Create a basic auth authenticator context
            context.setAuthenticator(handler);
            
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