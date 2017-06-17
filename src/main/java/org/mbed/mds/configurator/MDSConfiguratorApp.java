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
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLDecoder;
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
 * web-based customization of mDS and its sub-components (including the optional CB Bridge)
 * 
 * @author Doug Anson
 */
public class MDSConfiguratorApp 
{
    /**
     * MDSConfigurator - the primary class implementing MDSConfiguratorApp
     */
    static class MDSConfigurator extends BasicAuthenticator implements HttpHandler {   
        private static final String TITLE = "mbed Services Configuration";        // Title
        private static final int NUM_TABLES = 7;                                  // max number of tables shown
        
        private static final String DIV_HIDER_TAG = "__HIDE_TABLE_";              // DIV hiding table tag
        private static final String DIV_HIDE = "div#__NAME__ { display: none; }"; // DIV hide directive template
        
        private static final String SCRIPTS_ROOT = "./scripts/";                  // directory relative to jar file for scripts...
        private static final String CONFIG_FILES_ROOT = "/conf/";                 // directory relative to jar file for config files...
        private static final String TEMPLATES_ROOT = "/templates/";               // directory relative to jar file for html templates...
        
        private static final int DEFAULT_EXTRA_SLOTS = 5;                         // number of extra slots to insert into UI for new config entries
        private static final String DEFAULT_EMPTY_SLOT_KEY = "unused";            // empty slot key
        private static final String DEFAULT_EMPTY_SLOT_VALUE = "unused";          // empty slot value
        
        private final Properties m_mds_config_properties;                         // mDS Properties
        private final Properties m_mds_creds_properties;                          // mDS Credential Properties
        private final Properties m_http_coap_media_types_properties;              // HTTP CoAP Media Types Properties
        private final Properties m_logging_properties;                            // Logging Properties
        private final Properties m_connector_bridge_properties;                   // Connector Bridge Properties
        private final Properties m_shadow_service_properties;                     // Shadow Service Properties
        private final Properties m_mds_config_properties_updated;                 // mDS Updated Properties (deltas)
        private final Properties m_configurator_properties;                       // mDSConfigurator App (self) properties
        
        /**
         * Default Constructor
         */
        public MDSConfigurator() {
            super("get");
            this.m_mds_config_properties = new Properties();
            this.m_mds_creds_properties = new Properties();
            this.m_http_coap_media_types_properties = new Properties();
            this.m_logging_properties = new Properties();
            this.m_connector_bridge_properties = new Properties();
            this.m_shadow_service_properties = new Properties();
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
                String fq_filename = current + MDSConfigurator.TEMPLATES_ROOT + filename;
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
            html = html.replace("__TITLE__",MDSConfigurator.TITLE);
            
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
        private Properties getProperties(Properties prop,String filename) {
            InputStream input = null;
            try {
                String fq_filename = this.getWorkingDirectory() + MDSConfigurator.CONFIG_FILES_ROOT + filename;
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
            for(int i=0;i<MDSConfigurator.DEFAULT_EXTRA_SLOTS;++i) {
                Object put = props.put(MDSConfigurator.DEFAULT_EMPTY_SLOT_KEY + "-" + (i+1),MDSConfigurator.DEFAULT_EMPTY_SLOT_VALUE);
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
         * Display the Connector Bridge properties as HTML
         */
        private String displayConnectorBridgeConfig(String html) {
            if (this.m_connector_bridge_properties.isEmpty()) {
                this.getProperties(this.m_connector_bridge_properties,"gateway.properties");
            }
            return this.buildConfigurationTable(html,this.m_connector_bridge_properties,"gateway.properties","__CONNECTOR_BRIDGE_CONFIG_TABLE__",true,true);  // filter
        }
        
        /**
         * Display the Shadow Service properties as HTML
         */
        private String displayShadowServiceConfig(String html) {
            if (this.m_shadow_service_properties.isEmpty()) {
                this.getProperties(this.m_shadow_service_properties,"shadow-service.properties");
                this.addEmptyConfigSlots(this.m_shadow_service_properties);
            }
            return this.buildConfigurationTable(html,this.m_shadow_service_properties,"shadow-service.properties","__SHADOW_SERVICE_CONFIG_TABLE__",true,true);  // filter
        }
        
        /**
         * Display the Configurator App Admin (self) properties as HTML
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
            System.out.println("Device Server Configuration: Updating " + key + " = " + value);
            
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
                if (key.contains(MDSConfigurator.DEFAULT_EMPTY_SLOT_KEY)) {
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
                System.out.println("config(new key): Setting " + new_key + " = " + value);
                
                // delete the old preference
                props.remove(key);

                // put a new key with the updated value
                props.put(new_key, value);
            }
            else {
                // DEBUG
                System.out.println("config: Updating " + key + " = " + value);

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
         * Update the Connector Bridge properties file
         */
        private void updateConnectorBridgeConfiguration(String key,String value,String file,String new_key) {
            // DEBUG
            System.out.println("Connector Bridge Configuration: Updating " + key + " = " + value);
            
            // save the updated value in preferences
            this.m_connector_bridge_properties.put(key, value);

            // save the file
            this.saveConnectorBridgeConfigFile();
        }
        
        /**
         * Update the Shadow Service properties file
         */
        private void updateShadowServiceConfiguration(String key,String value,String file,String new_key) {
            // update expandable configuration
            this.updateExpandableConfiguration(this.m_shadow_service_properties,key,value,file,new_key);
            
            // clear out the empty slots
            this.clearEmptyConfigSlots(this.m_shadow_service_properties);
            
            // DEBUG
            System.out.println("Shadow Service Configuration: Updating " + key + " = " + value);
            
            // save the updated value in preferences
            this.m_shadow_service_properties.put(key, value);

            // save the file
            this.saveShadowServiceConfigFile();
            
            // put back the empty config slots
            this.addEmptyConfigSlots(this.m_shadow_service_properties);
        }
        
        /**
         * Update the mDSConfigurator App (self) properties file
         */
        private void updateConfiguratorConfiguration(String key,String value,String file) {
            // DEBUG
            System.out.println("Configurator Admin Configuration: Updating " + key + " = " + value);

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
                //System.out.println("Executing: " + this.SCRIPTS_ROOT + script);
                Runtime.getRuntime().exec(MDSConfigurator.SCRIPTS_ROOT + script);
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
                    String fq_filename = this.getWorkingDirectory() + MDSConfigurator.CONFIG_FILES_ROOT + filename;
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
            System.out.println("Saving Device Server Configuration File...");
            
            // write a new file out...
            boolean written = this.writePropertiesFile("Device Server Configuration Updates",this.m_mds_config_properties_updated, "deviceserver.properties.updated");
            
            // execute the recombination script to merge with the default config file...
            if (written) this.executeScript("mergeDeviceServerConfiguration.sh");
        }
        
        /**
         * Save the device server credentials configuration file
         */
        private void saveDeviceServerCredentialsFile() {
            // DEBUG
            System.out.println("Saving Device Server Credentials File...");
            
            // rewrite the file
            this.writePropertiesFile("Device Server Credentials Updates",this.m_mds_creds_properties, "credentials.properties");
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
         * Save the Connector Bridge configuration file
         */
        private void saveConnectorBridgeConfigFile() {
            // DEBUG
            System.out.println("Saving Connector Bridge Config File...");
            
            // rewrite the file
            this.writePropertiesFile("Connector Bridge Updates",this.m_connector_bridge_properties, "gateway.properties");
        }
        
        /**
         * Save the Shadow Service configuration file
         */
        private void saveShadowServiceConfigFile() {
            // DEBUG
            System.out.println("Saving Shadow Service Config File...");
            
            // rewrite the file
            this.writePropertiesFile("Shadow Service Updates",this.m_shadow_service_properties, "shadow-service.properties");
        }
        
        /**
         * Save the mDSConfigurator App (self) configuration file
         */
        private void saveConfiguratorConfigFile() {
            // DEBUG
            System.out.println("Saving Configurator Admin Config File...");
            
            // rewrite the file
            this.writePropertiesFile("Configurator Admin Updates",this.m_configurator_properties, "configurator.properties");
        }
        
        /**
         * If the configuration table has no entries, hide it from display (using CCS primatives)
         */
        private String checkAndHideTable(String html,String div_name,String table_index,Properties table_properties) {
            // build out the TAG
            String tag = MDSConfigurator.DIV_HIDER_TAG + table_index + "__";
            
            // see if we have properties
            if (table_properties.isEmpty()) {
                // hide the table via DIV...
                String div = MDSConfigurator.DIV_HIDE.replace("__NAME__", div_name);
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
            html = this.checkAndHideTable(html,"cb_config_table","5",this.m_connector_bridge_properties);
            html = this.checkAndHideTable(html,"shadow_service_config_table","6",this.m_shadow_service_properties);
            html = this.checkAndHideTable(html,"configurator_config_table","7",this.m_configurator_properties);
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
                    this.updateDeviceServerConfiguration(query.get("updated_key"),this.safeDecode(query.get("updated_value")), file);
                }
                
                // mDS Credentials
                if (file.equalsIgnoreCase("credentials.properties")) {
                    this.updateDeviceServerCredentials(query.get("updated_key"), this.safeDecode(query.get("updated_value")), file, query.get("new_key"));
                }
                
                // HTTP CoAP Media Types
                if (file.equalsIgnoreCase("http-coap-mediatypes.properties")) {
                    this.updateHTTPCoAPMediaTypes(query.get("updated_key"), this.safeDecode(query.get("updated_value")), file);
                }
                
                // Logging Configuration
                if (file.equalsIgnoreCase("log4j.properties")) {
                    this.updateLoggingConfiguration(query.get("updated_key"), this.safeDecode(query.get("updated_value")), file);
                }
                
                // Connector Bridge Configuration
                if (file.equalsIgnoreCase("gateway.properties")) {
                    this.updateConnectorBridgeConfiguration(query.get("updated_key"), this.safeDecode(query.get("updated_value")), file , query.get("new_key"));
                }
                
                // Shadow Service Configuration
                if (file.equalsIgnoreCase("shadow-service.properties")) {
                    this.updateShadowServiceConfiguration(query.get("updated_key"), this.safeDecode(query.get("updated_value")), file , query.get("new_key"));
                }
                
                // Configurator Configuration
                if (file.equalsIgnoreCase("configurator.properties")) {
                    this.updateConfiguratorConfiguration(query.get("updated_key"), this.safeDecode(query.get("updated_value")), file);
                }
            }
            
            // restart mDS
            if (query.get("mds") != null) {
                // also reset AWS IoT account creds 
                this.updateAWSCreds();
                
                // then restart the mDS server
                System.out.println("Restarting mbed Device Server...");
                this.executeScript("restartMDS.sh");
            }
            
            // restart Connector Bridge
            if (query.get("connectorbridge") != null) {
                // reset AWS IoT account creds 
                this.updateAWSCreds();
                
                // then restart the bridge
                System.out.println("Restarting Connector Bridge...");
                this.executeScript("restartConnectorBridge.sh");
            }
            
            // restart Shadow Service
            if (query.get("shadowservice") != null) {
                // then restart the shadow service
                System.out.println("Restarting Shadow Service...");
                this.executeScript("restartShadowService.sh");
            }
            
            // initialize the response
            html = this.initializeResponse(html);

            // build out the display response
            html = this.displayDeviceServerProperties(html);
            html = this.displayDeviceServerCredentials(html);
            html = this.displayCoAPMediaTypesConfig(html);
            html = this.displayLoggingConfig(html);
            html = this.displayConnectorBridgeConfig(html);
            html = this.displayShadowServiceConfig(html);
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
        
        // conditionally check and update the AWS IoT CLI creds - NOTE: sensitive to changes in the configuration file!
        private void updateAWSCreds() {
            String region = (String)this.m_connector_bridge_properties.get("aws_iot_region");
            String key_id = (String)this.m_connector_bridge_properties.get("aws_iot_access_key_id");
            String access_key = (String)this.m_connector_bridge_properties.get("aws_iot_secret_access_key");
            
            // DEBUG
            //System.out.println("updateAWSCreds: region: " + region + " key_id: " + key_id + " access_key: " + access_key);
            
            if (region != null && region.length() > 0 && region.equalsIgnoreCase("AWS_region_goes_here") == false &&
                key_id != null && key_id.length() > 0 && key_id.equalsIgnoreCase("AWS_Access_Key_ID_goes_here") == false && 
                access_key != null && access_key.length() > 0 && access_key.equalsIgnoreCase("AWS_Secret_Access_Key_goes_here") == false) {
                
                // set the AWS CLI creds...
                String args = region + " " + key_id + " " + access_key;
                
                // DEBUG
                //System.out.println("updateAWSCreds: calling: set_aws_creds.sh " + args);
                
                // execute
                this.executeScript("set_aws_creds.sh " + args);
            }
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
        
        // decode a URL-safe string
        private String safeDecode(String encoded_str) {
            try {
                System.out.println("Decoding: " + encoded_str);
                String tmp = URLDecoder.decode(encoded_str,"UTF-8");
                return new String(this.decode(tmp),"UTF-8");
            }
            catch (Exception ex) {
                System.out.println("EXCEPTION Decoding: " + encoded_str + " Message: " + ex.getMessage());
                return encoded_str;
            }
        }
        
        // Local Base64 Encode - Author/Credit: https://gist.github.com/EmilHernvall/953733
        private String encode(byte[] data)
        {
            char[] tbl = {
                'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P',
                'Q','R','S','T','U','V','W','X','Y','Z','a','b','c','d','e','f',
                'g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v',
                'w','x','y','z','0','1','2','3','4','5','6','7','8','9','+','/' };

            StringBuilder buffer = new StringBuilder();
            int pad = 0;
            for (int i = 0; i < data.length; i += 3) {

                int b = ((data[i] & 0xFF) << 16) & 0xFFFFFF;
                if (i + 1 < data.length) {
                    b |= (data[i+1] & 0xFF) << 8;
                } else {
                    pad++;
                }
                if (i + 2 < data.length) {
                    b |= (data[i+2] & 0xFF);
                } else {
                    pad++;
                }

                for (int j = 0; j < 4 - pad; j++) {
                    int c = (b & 0xFC0000) >> 18;
                    buffer.append(tbl[c]);
                    b <<= 6;
                }
            }
            for (int j = 0; j < pad; j++) {
                buffer.append("=");
            }

            return buffer.toString();
        }
        
        // Local Base64 Decode - - Author/Credit: https://gist.github.com/EmilHernvall/953733
        private byte[] decode(String data)
        {
            int[] tbl = {
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63, 52, 53, 54,
                55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2,
                3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1, -1, 26, 27, 28, 29, 30,
                31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
                48, 49, 50, 51, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };
            byte[] bytes = data.getBytes();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (int i = 0; i < bytes.length; ) {
                int b = 0;
                if (tbl[bytes[i]] != -1) {
                    b = (tbl[bytes[i]] & 0xFF) << 18;
                }
                // skip unknown characters
                else {
                    i++;
                    continue;
                }

                int num = 0;
                if (i + 1 < bytes.length && tbl[bytes[i+1]] != -1) {
                    b = b | ((tbl[bytes[i+1]] & 0xFF) << 12);
                    num++;
                }
                if (i + 2 < bytes.length && tbl[bytes[i+2]] != -1) {
                    b = b | ((tbl[bytes[i+2]] & 0xFF) << 6);
                    num++;
                }
                if (i + 3 < bytes.length && tbl[bytes[i+3]] != -1) {
                    b = b | (tbl[bytes[i+3]] & 0xFF);
                    num++;
                }

                while (num > 0) {
                    int c = (b & 0xFF0000) >> 16;
                    buffer.write((char)c);
                    b <<= 8;
                    num--;
                }
                i += 4;
            }
            return buffer.toByteArray();
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