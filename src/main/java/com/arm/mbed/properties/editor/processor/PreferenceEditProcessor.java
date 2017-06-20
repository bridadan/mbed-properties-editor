/**
 * @file    EditProcessor.java
 * @brief Preferences Editor Processor
 * @author Doug Anson
 * @version 1.0
 * @see
 *
 * Copyright 2017. ARM Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.arm.mbed.properties.editor.processor;

import com.arm.mbed.properties.editor.Main;
import com.arm.mbed.properties.editor.core.PreferenceEditor;
import com.arm.mbed.properties.editor.core.Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

/**
 * Preference Editor Processor
 * @author Doug Anson
 */
/**
* PreferenceEditProcessor - the primary class implementing mbedContainerConfiguratorApp
*/
public class PreferenceEditProcessor extends PreferenceEditor implements HttpHandler {   
   /**
    * Default Constructor
    * @param extendable_config
    */
   public PreferenceEditProcessor(boolean extendable_config) {
       super("get",extendable_config);
   }

   /**
    * initialize the HTTP response HTML content
    */
   private String initializeResponse(String html) {
       // initialize the table with the CSS first
       html += Utils.fileToString(this.m_templates_root,"css.html");

       // add scripts
       html += Utils.fileToString(this.m_templates_root,"scripts.html");

       // add the table templates/editor page
       html += Utils.fileToString(this.m_templates_root,this.m_editor);

       // update some of the key variables
       html = html.replace("__TITLE__",this.m_title);

       // return the html
       return html;
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
           Utils.executeScript(this.m_scripts_root,this.m_aws_set_creds_script + " " + args);
       }
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
       Map<String,String> query = Utils.queryToMap(t.getRequestURI().getQuery());

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
       
       // reset AWS IoT account creds 
       this.updateAWSCreds();
      
       // then restart the bridge
       System.out.println("Restarting Service...");
       Utils.executeScript(this.m_scripts_root,this.m_service_restart_script);

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