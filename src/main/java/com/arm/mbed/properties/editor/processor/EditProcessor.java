/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.arm.mbed.properties.editor.processor;

import com.arm.mbed.properties.editor.Main;
import com.arm.mbed.properties.editor.core.Utils;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author douans01
 */
/**
* EditProcessor - the primary class implementing mbedContainerConfiguratorApp
*/
public class EditProcessor extends BasicAuthenticator implements HttpHandler {   
   private static final String DEFAULT_CONFIG = "connector-bridge";                // Default Config
   private static final String DEFAULT_TITLE = "Configuration";                    // Default Title
   
   private String m_div_hider_tag = null;                                          // DIV hiding table tag
   private String m_hide_template = null;                                          // DIV hid directive template
   private String m_scripts_root = null;                                           // directory relative to jar file for scripts...
   private String m_config_files_root = null;                                      // directory relative to jar file for config files...
   private String m_templates_root = null;                                         // directory relative to jar file for html templates...
   private String m_empty_slot_key = null;                                         // empty slot key
   private String m_empty_slot_value = null;                                       // empty slot value

   private final Properties m_properties;                                          // Properties to edit
   private final Properties m_editor_properties;                                   // EditProcessor (self) properties

   private String m_editor = null;                                                 // which editor.html to use
   private String m_properties_file = null;                                        // which properties file to open an use
   private String m_editor_properties_file = null;                                 // EditProcessor (self) properties file
   private String m_title = DEFAULT_TITLE;                                         // our title
   private String m_config = DEFAULT_CONFIG;                                       // our configuration type
   private String m_configurator_config_fields = null;                             // config_fields (self)
   private String m_config_fields = null;                                          // config_fields option
   private boolean m_extendable_config = false;                                    // config is extenable (default is FALSE)

   // Shadow Service is currently the only option where extended config fields are utilized
   private ArrayList<String> m_extended_config_fields = null;                   

   /**
    * Default Constructor
    */
   public EditProcessor() {
       super("get");
       this.m_properties = new Properties();
       this.m_editor_properties = new Properties();
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
           String fq_filename = current + EditProcessor.this.m_templates_root + filename;
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
       html += this.fileToString(this.m_editor);

       // update some of the key variables
       html = html.replace("__TITLE__",this.m_title);

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
    * Open properties and read from the properties file (FQ name)
    */
   private Properties getPropertiesFQ(Properties prop,String fq_filename) {
       InputStream input = null;
       try {
           //System.out.println("Opening File: " + fq_filename);
           input = new FileInputStream(fq_filename);
           prop.clear();
           prop.load(input);
           input.close();

           // provide some defaults
           this.m_config_fields = prop.getProperty("config_fields",null);
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

   /**
    * Open Properties and read in from properties file
    */
   private Properties getProperties(Properties prop,String filename) {
       String fq_filename = this.getWorkingDirectory() + this.m_config_files_root + filename;
       return this.getPropertiesFQ(prop, fq_filename);
   }

   // config_fields being used
   private boolean configFieldsEnabled() {
       return !(this.m_config_fields == null);
   }

   // get the config_fields array
   private String[] getConfigFields(String config_fields) {
       if (config_fields != null && config_fields.length() > 0 && config_fields.contains(";") == true) {
           return config_fields.split(";");
       }
       return new String[0];
   }

   /**
    * Build out the HTML table representing the properties file 
    */
   private String createConfigTableAsHTMLFiltered(Properties props,String file, boolean editable_key,String config_fields) {
       // start the table
       String table = "<table border=\"0\">";
       boolean shown = true;

       // enumerate through the properties and fill the table
       String[] fields_to_show = this.getConfigFields(config_fields);
       for(int i=0;i<fields_to_show.length;++i) {
           String key = fields_to_show[i];
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

       // add the trailing tag
       table += "</table>";

       // return the table
       return table;
   }

   /**
    * Build out the HTML table representing the properties file 
    */
   private String createConfigTableAsHTMLNoFilter(Properties props,String file, boolean editable_key) {
       // start the table
       String table = "<table border=\"0\">";
       boolean shown = true;

       // enumerate through the properties and fill the table
       Enumeration e = props.propertyNames();
       while (e.hasMoreElements()) {
           String key = (String) e.nextElement();
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

       // add the trailing tag
       table += "</table>";

       // return the table
       return table;
   }

   /**
    * Build out the configuration table (properties from a properties file) as HTML content
    */
   private String buildConfigurationTable(String html,Properties props,String file,String key,String config_fields) { 
       return this.buildConfigurationTable(html, props, file, key, false, config_fields);
   }

   /**
    * Build out the configuration table (properties from a properties file) as HTML content
    */
   private String buildConfigurationTable(String html,Properties props,String file,String key,boolean editable_key,String config_fields) {
       String preference_table = "";

       // see if we have config_fields enabled... if we do, we filter and order based on that...
       if (this.configFieldsEnabled() == true) {
           // create the actual configuration table has HTML
           preference_table = this.createConfigTableAsHTMLFiltered(props,file,editable_key,config_fields);
       }
       else {
           // create the actual configuration table has HTML
           preference_table = this.createConfigTableAsHTMLNoFilter(props,file,editable_key);
       }

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
    * Add some empty configuration slots in the configuration table for adding new config entries
    */
   private void addEmptyConfigSlots(Properties props) {
       props.remove(this.m_empty_slot_key);
       props.put(this.m_empty_slot_key,this.m_empty_slot_value);
   }

   /**
    * Display the properties as HTML
    */
   private String displayConfig(String html) {
       if (this.m_properties.isEmpty()) {
           this.getProperties(this.m_properties,this.m_properties_file);
           this.addEmptyConfigSlots(this.m_properties);
       }
       return this.buildConfigurationTable(html,this.m_properties,this.m_properties_file,"__CONFIG_TABLE__",true,this.m_config_fields);
   }

   /**
    * Display the Configurator App Admin (self) properties as HTML
    */
   private String displayConfiguratorConfig(String html) {
       if (this.m_editor_properties.isEmpty()) this.getProperties(this.m_editor_properties,this.m_editor_properties_file);
       return this.buildConfigurationTable(html,this.m_editor_properties,m_editor_properties_file,"__CONFIGURATOR_CONFIG_TABLE__",this.m_configurator_config_fields);
   }

   /**
    * Clear out the non-used extra configuration entry slots - don't try to store them...
    */
   private void clearEmptyConfigSlots(Properties props) {
       Enumeration e = props.propertyNames();
       while (e.hasMoreElements()) {
           String key = (String) e.nextElement();
           if (key.contains(this.m_empty_slot_key)) {
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
    * Update the properties file
    */
   private void updateProperties(String key,String value,String file,String new_key) {
       if (key.equalsIgnoreCase(this.m_empty_slot_key) == true) {
           if (new_key.equalsIgnoreCase(this.m_empty_slot_key) == true) {
               // note that you must edit the KEY as well as the value
               System.out.println("updateProperties: You have to edit KEY and VALUE to add a new value to the properties list.. ignoring");

               // clear out the empty slots
               this.clearEmptyConfigSlots(this.m_properties);
           }
           else {
               // DEBUG
               System.out.println("updateProperties: Adding new key/value entry: " + key + " = " + value);

               // update expandable configuration
               this.updateExpandableConfiguration(this.m_properties,key,value,file,new_key);

               // update the expanded config_fields list
               this.m_extended_config_fields.add(new_key);
           }

           // clear out the empty slots
           this.clearEmptyConfigSlots(this.m_properties);
       }
       else {
           // DEBUG
           System.out.println("updateProperties: Updating " + key + " = " + value);

           // save the updated value in preferences
           this.m_properties.put(key, value);
       }

       // save the file
       this.savePropertiesFile();

        // empty our config file additions
       this.m_extended_config_fields.clear();

       // put back the empty config slots
       this.addEmptyConfigSlots(this.m_properties);
   }

   /**
    * Update the mDSConfigurator App (self) properties file
    */
   private void updatedPropertiesEditorConfiguration(String key,String value,String file) {
       // DEBUG
       System.out.println("updatedPropertiesEditorConfiguration: Updating " + key + " = " + value);

       // save the updated value the preferences
       this.m_editor_properties.put(key, value);

       // save the file
       this.saveConfiguratorConfigFile();
   }

   /**
    * Execute a script
    */
   private void executeScript(String script) {
       try {
           //System.out.println("Executing: " + this.m_scripts_root + "/" + script);
           Runtime.getRuntime().exec(this.m_scripts_root + "/" + script);
       } catch (IOException ex) {
           System.out.println("Exception caught: " + ex.getMessage() + " script: " + script);
       }
   }

   /**
    * Write a properties file
    */
   private boolean writePropertiesFile2(Properties props,String filename) {
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
               String fq_filename = this.getWorkingDirectory() + this.m_config_files_root + filename;
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
    * Save the properties file
    */
   private void savePropertiesFile() {
       // updating the config_fields for any new fields to display
       String added_config_fields = "";
       for(int i=0;i<this.m_extended_config_fields.size();++i) {
           added_config_fields += this.m_extended_config_fields.get(i);
           if (i < this.m_extended_config_fields.size()-1) {
               added_config_fields += ";";
           }
       }

       // append to config_fields if we have made additions...put the empty one back at the end of the config_fields value...
       if (added_config_fields.length() > 0) {
           this.m_config_fields = this.m_config_fields.replace(this.m_empty_slot_key,added_config_fields);
           this.m_config_fields += ";" + this.m_empty_slot_key;
           this.m_properties.put(("config_fields"), this.m_config_fields);
       }

       // DEBUG
       System.out.println("Saving Properties File...");

       // rewrite the file (with comments)
       this.writePropertiesFile("Updated property file",this.m_properties,this.m_properties_file);
   }

   /**
    * Save the mDSConfigurator App (self) configuration file
    */
   private void saveConfiguratorConfigFile() {
       // DEBUG
       System.out.println("Saving Configurator Properties File...");

       // rewrite the file
       this.writePropertiesFile("Updated configurator property file",this.m_editor_properties, this.m_editor_properties_file);
   }

   /**
    * If the configuration table has no entries, hide it from display (using CCS primatives)
    */
   private String checkAndHideTable(String html,String div_name,String table_index,Properties table_properties) {
       // build out the TAG
       String tag = this.m_div_hider_tag + table_index + "__";

       // see if we have properties
       if (table_properties.isEmpty()) {
           // hide the table via DIV...
           String div = this.m_hide_template.replace("__NAME__", div_name);
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
       html = this.checkAndHideTable(html,"config_table","1",this.m_properties);
       html = this.checkAndHideTable(html,"configurator_config_table","2",this.m_editor_properties);
       return html;
   }

   // conditionally check and update the AWS IoT CLI creds - NOTE: sensitive to changes in the configuration file!
   private void updateAWSCreds() {
       String region = (String)this.m_properties.get("aws_iot_region");
       String key_id = (String)this.m_properties.get("aws_iot_access_key_id");
       String access_key = (String)this.m_properties.get("aws_iot_secret_access_key");

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
    * @param editor_config_file - name of the property editor properties file (fully qualified)
    */
   public void loadProperties(String editor_config_file) {
       this.getPropertiesFQ(this.m_editor_properties, editor_config_file);

       // establish defaults
       this.m_title = this.getProperty("title");
       this.m_config = this.getProperty("config");
       this.m_configurator_config_fields = this.getProperty("config_fields");

       // default editor.html is the full one
       this.m_editor = this.getProperty("full_editor");
       
       // other config items
       this.m_div_hider_tag = this.getProperty("div_hider_tag");
       this.m_hide_template = this.getProperty("div_hide");
       this.m_scripts_root = this.getProperty("scripts_root");
       this.m_config_files_root = this.getProperty("config_files_root");
       this.m_templates_root = this.getProperty("templates_root");
       this.m_empty_slot_key = this.getProperty("default_key");
       this.m_empty_slot_value = this.getProperty("default_value");
       
       // initialize the extendable properties tmp list
       this.m_extended_config_fields = new ArrayList<String>();

       // determine which config drives which editor.html
       if (this.m_config != null && this.m_config.equalsIgnoreCase("connector-bridge")) {
           // DEBUG
           System.out.println("Configurator: Connector-Bridge configuration...");

           // connector-bridge editor.html
           this.m_editor = this.getProperty("cb_editor");
           this.m_properties_file = this.getProperty("cb_properties_file");
       }
       
       if (this.m_config != null && this.m_config.equalsIgnoreCase("shadow-service")) {
           // DEBUG
           System.out.println("Configurator: Shadow-Service configuration...");

           // shadow-service editor.html
           this.m_editor = this.getProperty("ss_editor");
           this.m_properties_file = this.getProperty("ss_properties_file");

           // we have to extend this configuration
           this.m_extendable_config = true;
       }
   }

   /**
    * Get a string-based property for mDSConfigurator App (self)
    * @param key - name of the property
    * @return - the property value if found, NULL otherwise
    */
   public String getProperty(String key) {
       String prop = (String)this.m_editor_properties.get(key);
       if (prop != null && prop.length() > 0) {
           return prop;
       }
       return null;
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

           // Configurator Configuration
           if (file.equalsIgnoreCase(Main.CONFIGURATOR_PROPERTIES)) {
               // Update Configurator Configuration
               this.updatedPropertiesEditorConfiguration(query.get("updated_key"),Utils.urlsafe_base64_decode(query.get("updated_value")),file);
           }
           else {
               // Update Configuration
               this.updateProperties(query.get("updated_key"),Utils.urlsafe_base64_decode(query.get("updated_value")),file,query.get("new_key"));
           }
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
           // then restart the Shadow service
           System.out.println("Restarting Shadow Service...");
           this.executeScript("restartShadowService.sh");
       }

       // initialize the response
       html = this.initializeResponse(html);

       // build out the display response
       html = this.displayConfig(html);
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
