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
        public MDSConfigurator() {
            super();
        }
        
        private String fileToString(String filename)  {
            String contents = "";
            InputStream input = null;
            
            try {
                String current = new java.io.File( "." ).getCanonicalPath();
                String fq_filename = current + "/"  + filename;
                System.out.println("fileToString: Reading File: "+ fq_filename);
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
        
        private String initializeTable() {
            String table = "";
            
            // initialize the table with the CSS first
            table += this.fileToString("css.html");
            
            // add scripts
            table += this.fileToString("scripts.html");
            
            // add the table template
            table += this.fileToString("editor.html");
            
            // update some of the key variables
            String dir = this.getWorkingDirectory();
            table = table.replace("__TITLE__","mDS Configuration Editor v1.0");
            
            // return the table
            return table;
        }
        
        // open the mDS device server properties
        private Properties getDeviceServerProperties() { return this.getProperties("deviceserver.properties"); }
        
        // open the mDS device server credential properties
        private Properties getDeviceServerCredentialProperties() { return this.getProperties("credentials.properties"); }
        
        // open the mDS device server logging properties
        private Properties getDeviceServerLoggingProperties() { return this.getProperties("log4j.properties"); }
        
        // open the mDS device server medai types properties
        private Properties getDeviceServerMediaTypesProperties() { return this.getProperties("http-coap-mediatypes.properties"); }
        
        private String getWorkingDirectory() {
            try {
                return new java.io.File( "." ).getCanonicalPath();
            }
            catch (Exception ex) {
            }
            return "./";
        }
        // open the mDS properties
        private Properties getProperties(String filename) {
            Properties prop = new Properties();
            try {
                String current = this.getWorkingDirectory();
                String fq_filename = current + "/"  + filename;
                System.out.println("Reading Properties File: "+ fq_filename);
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
        private String createMDSConfigurationTableAsHTML(Properties props) {
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
                String save_button = "<button name=\"save_button\" value=\"" + key + "\" type=\"button\" onclick=saveData('" + key + "') style=\"height:35px;width:80px\">SAVE</button>";
                table += "<td align=\"center\" height=\"35px\" width=\"210px\">" + save_button + "</td>";
     
                // finish row
                table += "</tr>";
            }
            
            // add the trailing tag
            table += "</table>";
                        
            // return the table
            return table;
        }
        
        private String buildConfigurationTable(Properties props) {
            // initialize the table
            String table = this.initializeTable();
            
            // create the actual configuration table has HTML
            String preference_table = this.createMDSConfigurationTableAsHTML(props);
            
            // fill in the body
            table = table.replace("__PREFERENCES_TABLE__",preference_table);
            
            // return the table
            return table;
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
        
        @Override
        public void handle(HttpExchange t) throws IOException {
            // configuration page request (GET)
            Properties props = this.getDeviceServerProperties();
            Map<String,String> query = this.queryToMap(t.getRequestURI().getQuery());
            if (query.get("updated_key") != null) {
                // save the updated value the preferences
                String key = query.get("updated_key");
                String value = query.get("updated_value");
                props.put(key, value);
                // DEBUG
                System.out.println("Updating " + key + " = " + value);
            }
            if (query.get("save") != null) {
                // DEBUG
                System.out.println("Saving Configuration File...");
            }
            String response = this.buildConfigurationTable(props);
            t.sendResponseHeaders(200,response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
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

