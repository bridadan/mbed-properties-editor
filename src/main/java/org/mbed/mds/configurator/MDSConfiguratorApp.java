package org.mbed.mds.configurator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * mDS Configurator
 */
public class MDSConfiguratorApp 
{
    static class MDSConfigurator implements HttpHandler {   
        private Properties m_mds_config_properties;
        private Properties m_mds_creds_properties;
        private Properties m_http_coap_media_types_properties;
        private Properties m_logging_properties;
        
        private Properties m_mds_config_properties_updated;
        
        public MDSConfigurator() {
            super();
            this.m_mds_config_properties = new Properties();
            this.m_mds_creds_properties = new Properties();
            this.m_http_coap_media_types_properties = new Properties();
            this.m_logging_properties = new Properties();
            this.m_mds_config_properties_updated = new Properties();
        }
        
        private String fileToString(String filename)  {
            String contents = "";
            InputStream input = null;
            
            try {
                String current = new java.io.File( "." ).getCanonicalPath();
                String fq_filename = current + "/"  + filename;
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
            html = html.replace("__TITLE__","mDS Configuration Editor v1.0");
            
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
                prop.clear();
                String current = this.getWorkingDirectory();
                String fq_filename = current + "/"  + filename;
                InputStream input = new FileInputStream(fq_filename);
                prop.load(input);
                input.close();
            }
            catch (Exception ex) {
                System.out.println("Exception during Reading Properties File: " + ex.getMessage());
            }
            return prop;
        }
        // build out the properties table
        private String createConfigTableAsHTML(Properties props,String file) {
            // start the table
            String table = "<table border=\"0\">";
            
            // enumerate through the properties and fill the table
            Enumeration e = props.propertyNames();
            while (e.hasMoreElements()) {
                table += "<tr>";
                
                // Key
                String key = (String) e.nextElement();
                table += "<td>" + key + "</td>";
                
                // Value
                String height = "auto";
                String value = props.getProperty(key);               
                table += "<td id=\"" + key + "\" contenteditable=\"true\" align=\"left\" height=\"" + height + "\" width=\"" + "auto" + "\">" + value + "</td>";
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
            // create the actual configuration table has HTML
            String preference_table = this.createConfigTableAsHTML(props,file);
            
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
            Properties props = this.getProperties(this.m_mds_config_properties,"deviceserver.properties");
            return this.buildConfigurationTable(html,props,"deviceserver.properties","__DS_CONFIG_TABLE__");
        }
        
        private String displayDeviceServerCredentials(String html) {
            Properties props = this.getProperties(this.m_mds_creds_properties,"credentials.properties");
            return this.buildConfigurationTable(html,props,"credentials.properties","__DS_CREDS_TABLE__");
        }
        
        private String displayCoAPMediaTypesConfig(String html) {
            Properties props = this.getProperties(this.m_http_coap_media_types_properties,"http-coap-mediatypes.properties");
            return this.buildConfigurationTable(html,props,"http-coap-mediatypes.properties","__COAP_MEDIA_TYPE_TABLE__");
        }
        
        private String displayLoggingConfig(String html) {
            Properties props = this.getProperties(this.m_logging_properties,"log4j.properties");
            return this.buildConfigurationTable(html,props,"log4j.properties","__LOGGING_CONFIG_TABLE__");
        }
        
        // update mDS configuration
        private void updateDeviceServerConfiguration(String key,String value,String file) {
            // save the updated value the preferences
            this.m_mds_config_properties_updated.put(key, value);

            // DEBUG
            System.out.println("mDS Configuration: Updating " + key + " = " + value);
        }
        
        // update mDS credentials
        private void updateDeviceServerCredentials(String key,String value,String file) {
            // save the updated value the preferences
            this.m_mds_creds_properties.put(key, value);

            // DEBUG
            System.out.println("mDS Credentials: Updating " + key + " = " + value);
        }
        
        // update HTTP CoAP Media Types
        private void updateHTTPCoAPMediaTypes(String key,String value,String file) {
            // save the updated value the preferences
            this.m_http_coap_media_types_properties.put(key, value);

            // DEBUG
            System.out.println("HTTP CoAP Media Types: Updating " + key + " = " + value);
        }
        
        // update Logging Configuration
        private void updateLoggingConfiguration(String key,String value,String file) {
            // save the updated value the preferences
            this.m_logging_properties.put(key, value);

            // DEBUG
            System.out.println("Logging Configuration: Updating " + key + " = " + value);
        }
        
        private void saveDeviceServerConfigurationFile() {
            // DEBUG
            System.out.println("Saving mDS Configuration File...");
            
            // clear out the updates... 
            this.m_mds_config_properties_updated.clear();
        }
        
        private void saveDeviceServerCredentialsFile() {
            // DEBUG
            System.out.println("Saving mDS Credentials File...");
        }
        
        private void saveHTTPCoAPMediaTypesConfigFile() {
            // DEBUG
            System.out.println("Saving HTTP CoAP Media Types Config File...");
        }
        
        private void saveLoggingConfigFile() {
            // DEBUG
            System.out.println("Saving Logging Config File...");
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
                    this.updateDeviceServerCredentials(query.get("updated_key"), query.get("updated_value"), file);
                }
                
                // HTTP CoAP Media Types
                if (file.equalsIgnoreCase("http-coap-mediatypes.properties")) {
                    this.updateHTTPCoAPMediaTypes(query.get("updated_key"), query.get("updated_value"), file);
                }
                
                // Logging Configuration
                if (file.equalsIgnoreCase("log4j.properties")) {
                    this.updateLoggingConfiguration(query.get("updated_key"), query.get("updated_value"), file);
                }
            }
            
            // save configurations...
            if (query.get("save") != null) {
                String value = query.get("save");
                
                // mDS Configuration
                if (value.equalsIgnoreCase("ds")) {
                    this.saveDeviceServerConfigurationFile();
                }
                
                // mDS Credentials
                if (value.equalsIgnoreCase("creds")) {
                    this.saveDeviceServerCredentialsFile();
                }
                
                // HTTP CoAP Media Types
                if (value.equalsIgnoreCase("media")) {
                    this.saveHTTPCoAPMediaTypesConfigFile();
                }
                
                // Logging Configuration
                if (value.equalsIgnoreCase("logging")) {
                    this.saveLoggingConfigFile();
                }
            }
            
            // initialize the response
            html = this.initializeResponse(html);

            // build out the display response
            html = this.displayDeviceServerProperties(html);
            html = this.displayDeviceServerCredentials(html);
            html = this.displayCoAPMediaTypesConfig(html);
            html = this.displayLoggingConfig(html);
            
            // send the response and close
            t.sendResponseHeaders(200,html.length());
            OutputStream os = t.getResponseBody();
            os.write(html.getBytes());
            
            // clean up
            os.close();
        }
    }
    
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8234), 0);
        server.createContext("/", new MDSConfigurator());
        server.setExecutor(null);
        server.start();
    }
}

