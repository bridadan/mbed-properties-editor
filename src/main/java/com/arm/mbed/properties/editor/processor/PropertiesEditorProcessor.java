/**
 * @file    PropertiesEditorProcessor.java
 * @brief Properties Editor Processor
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
import com.arm.mbed.properties.editor.core.PropertiesEditor;
import com.arm.mbed.properties.editor.core.Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Properties Editor Processor - the primary class implementing a HTML processor for the properties editor
 * @author Doug Anson
 */
public class PropertiesEditorProcessor extends PropertiesEditor implements HttpHandler {  
    // Defaults
    private static String CSS_DEFAULT_FILE = "css.tpl";
    private static String SCRIPTS_DEFAULT_FILE = "scripts.tpl";
    
    // the HTTP verb we respond to...
    private static String HTTP_VERB_DEFAULT = "get";
    
    // template files
    private String m_css_file = null;
    private String m_scripts_file = null;
    
    // Human readable key map
    private HashMap<String,String> m_key_map = null;
    
    /**
     * Default Constructor
     * @param extendable_config
     */
    public PropertiesEditorProcessor(boolean extendable_config) {
        super(PropertiesEditorProcessor.HTTP_VERB_DEFAULT,extendable_config);
        this.initHumanReadableKeyMap();
        
        // pull in the CSS and scripts filenames
        this.m_css_file = this.getProperty("css_template");
        if (this.m_css_file == null || this.m_css_file.length() == 0) {
            this.m_css_file = CSS_DEFAULT_FILE;
        }
        this.m_scripts_file = this.getProperty("scripts_template");
        if (this.m_scripts_file == null || this.m_scripts_file.length() == 0) {
            this.m_scripts_file = SCRIPTS_DEFAULT_FILE;
        }
    }

    /**
     * initialize the HTTP response HTML content
     */
    private String initializeResponse(String html) {
        // initialize the table with the CSS first
        html += Utils.fileToString(this.m_templates_root,this.m_css_file);

        // add scripts
        html += Utils.fileToString(this.m_templates_root,this.m_scripts_file).replace("__SERVICE_NAME__", this.m_service_name);

        // add the table templates/editor page
        html += Utils.fileToString(this.m_templates_root,this.m_editor_file).replace("__SERVICE_NAME__", this.m_service_name);

        // update some of the key variables
        html = html.replace("__TITLE__",this.m_title).replace("__SERVICE_NAME__", this.m_service_name);

        // return the html
        return html;
    } 
    
    // initialize the human readable KeyMap
    private void initHumanReadableKeyMap() {
        this.m_key_map = new HashMap<String,String>();
        
        // for now... just make this static
        this.m_key_map.put("api_key","Pelion API Key");
        this.m_key_map.put("mds_api_token","Pelion API Key");
        this.m_key_map.put("mds_gw_address","Bridge IP Address Override");
        this.m_key_map.put("mds_enable_long_poll","Enable Bridge long Polling");
        this.m_key_map.put("api_endpoint_address","Pelion API Address");
        this.m_key_map.put("mds_address","Pelion API Address");
        this.m_key_map.put("mds_gw_port","Bridge Webhook Port");
        this.m_key_map.put("mds_bridge_error_level","Bridge Debug Level");
        this.m_key_map.put("mds_remove_on_deregistration","Remove Device on De-registration");
        this.m_key_map.put("iotf_legacy_bridge","Watson IoT Enable Legacy Mode");
        this.m_key_map.put("iotf_api_key","Watson IoT API Key");
        this.m_key_map.put("iotf_auth_token","Watson IoT Authentication Token");
        this.m_key_map.put("threads_core_pool_size","Bridge Threading Pool Size");
        this.m_key_map.put("threads_max_pool_size","Bridge Threading Max Pool Size");
        this.m_key_map.put("threads_keep_alive_time","Bridge Thread Keep Alive (sec)");
        this.m_key_map.put("aws_iot_region","AWS Region");
        this.m_key_map.put("aws_iot_access_key_id","AWS Access Key ID");
        this.m_key_map.put("aws_iot_secret_access_key","AWS Access Key Secret");
        this.m_key_map.put("google_cloud_auth_json","Google Cloud Auth JSON");
        this.m_key_map.put("google_cloud_region","Google Cloud Region");
        this.m_key_map.put("google_cloud_mqtt_port","Google MQTT Port");
        this.m_key_map.put("iot_event_hub_name","Microsoft IoTHub Name");
        this.m_key_map.put("iot_event_hub_sas_token","Microsoft IoTHub SAS Token (iothubowner)");
        this.m_key_map.put("mqtt_address","MQTT Broker IP Address");
        this.m_key_map.put("mqtt_port","MQTT Broker Port");
        this.m_key_map.put("mqtt_use_ssl","MQTT Connection Using SSL");
        this.m_key_map.put("mqtt_username","MQTT Connection Username");
        this.m_key_map.put("mqtt_password","MQTT Connection Password");
        this.m_key_map.put("mqtt_client_id","MQTT Connection ClientID");
        this.m_key_map.put("mqtt_mds_topic_root","MQTT Topic Root");
        this.m_key_map.put("mqtt_no_client_creds","MQTT Broker Using Client Creds");
        this.m_key_map.put("mqtt_import_keystore","MQTT Broker Using Keystore");
    }
    
    // map the properties key to a human readable form
    private String mapKeyToHumanReadable(String key) {
        if (this.m_key_map != null && this.m_key_map.containsKey(key) == true) {
            return this.m_key_map.get(key);
        }
        return key;
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
            // Get the Key
            String key = fields_to_show[i];
            
            // Get the Value
            String value = props.getProperty(key);     
            
            // Convert the Key to human readable
            String readable_key = this.mapKeyToHumanReadable(key);
            
            table += "<tr>";

            // Create the Key HTML
            if (editable_key)
                table += "<td id=\"" + key + "-key\" contenteditable=\"true\">" + readable_key + "</td>&nbsp;&nbsp";
            else
                table += "<td id=\"" + key + "-key\" contenteditable=\"false\">" + readable_key + "</td>&nbsp;&nbsp";

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
            // Get the Key
            String key = (String) e.nextElement();
            
            // Get the Value
            String value = props.getProperty(key);     
            
            // Convert the Key to human readable
            String readable_key = this.mapKeyToHumanReadable(key);
            
            table += "<tr>";

            // Key
            if (editable_key)
                table += "<td id=\"" + key + "-key\" contenteditable=\"true\">" + readable_key + "</td>";
            else
                table += "<td id=\"" + key + "-key\" contenteditable=\"false\">" + readable_key + "</td>";

            // Value          
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
     * Display the PropertiesEditor App Admin (self) properties as HTML
     */
    private String displayPropertiesEditorConfig(String html) {
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
        
        // remove all new lines...
        if (value != null) {
            value = Utils.replaceAllCharOccurances(value,(char)160,' ');
            value = value.trim();
        }
        
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
     * Update the mDSPropertiesEditor App (self) properties file
     */
    private void updatedPropertiesEditorConfiguration(String key,String value,String file) {
        // DEBUG
        System.out.println("updatedPropertiesEditorConfiguration: Updating " + key + " = " + value);

        // save the updated value the preferences
        this.m_editor_properties.put(key, value);

        // save the file
        this.savePropertiesEditorConfigFile();
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

            // Properties Editor Configuration
            if (file.equalsIgnoreCase(Main.PROPERTIES_EDITOR_DEFAULT_CONFIG)) {
                // Update PropertiesEditor Configuration
                this.updatedPropertiesEditorConfiguration(query.get("updated_key"),Utils.urlsafe_base64_decode(query.get("updated_value")),file);
            }
            else {
                // Update Configuration
                this.updateProperties(query.get("updated_key"),Utils.urlsafe_base64_decode(query.get("updated_value")),file,query.get("new_key"));
            }
        }

        // reset AWS IoT account creds 
        this.updateAWSCreds();
        
        // see if the parameters state that we want to restart
        if (query.containsKey("service") && query.get("service") != null && query.get("service").equalsIgnoreCase("restart") == true) {
            // then restart!
            System.out.println("Restarting Service...");
            Utils.executeScript(this.m_scripts_root,this.m_service_restart_script);
        }

        // initialize the response
        html = this.initializeResponse(html);

        // build out the display response
        html = this.displayConfig(html);
        html = this.displayPropertiesEditorConfig(html);

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
