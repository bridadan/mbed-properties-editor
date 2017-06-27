/**
 * @file    PropertiesEditor.java
 * @brief Base class for a properties editor
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
package com.arm.mbed.properties.editor.core;

import com.sun.net.httpserver.BasicAuthenticator;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Properties Editor Base Class
 * @author Doug Anson
 */
public class PropertiesEditor extends BasicAuthenticator {
   private static final String DEFAULT_TITLE = "Configuration";                      // Default Title
   
   protected String m_div_hider_tag = null;                                          // DIV hiding table tag
   protected String m_hide_template = null;                                          // DIV hid directive template
   protected String m_scripts_root = null;                                           // directory relative to jar file for scripts...
   protected String m_config_files_root = null;                                      // directory relative to jar file for config files...
   protected String m_templates_root = null;                                         // directory relative to jar file for html templates...
   protected String m_empty_slot_key = null;                                         // empty slot key
   protected String m_empty_slot_value = null;                                       // empty slot value

   protected String m_editor = null;                                                 // which editor.html to use
   protected String m_properties_file = null;                                        // which properties file to open an use
   protected String m_editor_properties_file = null;                                 // EditProcessor (self) properties file
   protected String m_title = DEFAULT_TITLE;                                         // our title
   protected String m_configurator_config_fields = null;                             // config_fields (self)
   protected String m_config_fields = null;                                          // config_fields option
   protected String m_service_name = null;                                           // service name

   protected Properties m_properties = null;                                         // Properties to edit
   protected Properties m_editor_properties = null;                                  // EditProcessor (self) properties
      
   // For option where extended config fields are utilized
   protected ArrayList<String> m_extended_config_fields = null;                
   protected boolean m_extendable_config = false;                                    // config is extenable (default is FALSE)

   // scripts
   protected String m_service_restart_script = null;                                 // service restart script
   protected String m_aws_set_creds_script = null;                                   // Special AWS creds handling script  

    // default constructor
    public PropertiesEditor(String verb,boolean extendable_config) {
        super(verb);
        this.m_properties = new Properties();
        this.m_editor_properties = new Properties();
        this.enableExtendableConfig(extendable_config);
    }
    
    /**
    * Load up the mDSPropertiesEditor App (self) properties file
    * @param editor_config_file - name of the property editor properties file (fully qualified)
    */
    public void loadProperties(String editor_config_file) {
        // get the properties-editor configuration
        Utils.getPropertiesFQ(this.m_editor_properties,editor_config_file);

        // establish defaults
        this.m_title = this.getProperty("title");
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
        this.m_service_name = this.getProperty("service_name");

        // initialize the extendable properties tmp list
        this.m_extended_config_fields = new ArrayList<String>();
        
        // editor template and properties file
        this.m_editor = this.getProperty("editor_template");
        this.m_properties_file = this.getProperty("properties_file");
        
        // get the accessed scripts
        this.m_service_restart_script = this.getProperty("service_restart_script");
        this.m_aws_set_creds_script = this.getProperty("aws_script");
        
        // enable/disable the extendable config feature
        this.extendableConfig();
    }
   
    /**
    * Get a string-based property for mDSPropertiesEditor App (self)
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
     * Get an integer-based property for mDSPropertiesEditor App (self)
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
     * Write a properties file (with comments)
     * @param comments
     * @param props
     * @param filename
     * @return 
     */
    protected boolean writePropertiesFile(String comments,Properties props,String filename) {
        OutputStream output = null;
        boolean written = false;

        if (props.isEmpty() == false) {
            try {
                String fq_filename = Utils.getWorkingDirectory() + this.m_config_files_root + filename;
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
    * Save the mDSPropertiesEditor App (self) configuration file
    */
    protected void savePropertiesEditorConfigFile() {
       // DEBUG
       System.out.println("Saving PropertiesEditor Properties File...");

       // rewrite the file
       this.writePropertiesFile("Updated configurator property file",this.m_editor_properties, this.m_editor_properties_file);
    }
    
    /**
    * Save the properties file
    */
    protected void savePropertiesFile() {
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
    
    // set the extendable config
    private void enableExtendableConfig(boolean extendable_config) {
        this.m_extendable_config = extendable_config;
    }
    
    // determine if we want to enable extendable configuratoin
    protected void extendableConfig() {
        String enabled = this.m_editor_properties.getProperty("extendable_config");
        if (enabled != null && enabled.equalsIgnoreCase("true") == true) {
            this.enableExtendableConfig(true);
        }
    }
    
    // get the config_fields array
    protected String[] getConfigFields(String config_fields) {
        if (config_fields != null && config_fields.length() > 0 && config_fields.contains(";") == true) {
            return config_fields.split(";");
        }
        return new String[0];
    }
   
    /**
    * Add some empty configuration slots in the configuration table for adding new config entries
    * @param props
    */
    protected void addEmptyConfigSlots(Properties props) {
        props.remove(this.m_empty_slot_key);
        props.put(this.m_empty_slot_key,this.m_empty_slot_value);
    }
    
    /**
    * Open Properties and read in from properties file
     * @param prop
     * @param filename
     * @return 
    */
    protected Properties getProperties(Properties prop,String filename) {
       String fq_filename = Utils.getWorkingDirectory() + this.m_config_files_root + filename;
       Properties filled_props = Utils.getPropertiesFQ(prop, fq_filename);
       
       // provide some defaults
       this.m_config_fields = filled_props.getProperty("config_fields",null);
       
       // return the props
       return filled_props;
    }

    // config_fields being used
    protected boolean configFieldsEnabled() {
        return !(this.m_config_fields == null);
    }
    
    /**
     * Clear out the non-used extra configuration entry slots - don't try to store them...
     * @param props
     */
   protected void clearEmptyConfigSlots(Properties props) {
       Enumeration e = props.propertyNames();
       while (e.hasMoreElements()) {
           String key = (String) e.nextElement();
           if (key.contains(this.m_empty_slot_key)) {
               props.remove(key);
           }
       }
   }
    
    // Override for BasicAuthenticator
    @Override
    public boolean checkCredentials(String string, String string1) {
        // default is false
        return false;
    }
    
}
