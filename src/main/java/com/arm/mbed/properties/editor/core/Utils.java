/**
 * @file    Utils.java
 * @brief misc collection of static utilities
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Static support utilities
 *
 * @author Doug Anson
 */
public class Utils {
    /**
    * Execute a script
     * @param root
     * @param script
    */
    public static void executeScript(String root,String script) {
        try {
            //System.out.println("Executing: " + this.m_scripts_root + "/" + script);
            Runtime.getRuntime().exec(root + "/" + script);
        } 
        catch (IOException ex) {
            // Error
            System.out.println("Exception caught: " + ex.getMessage() + " script: " + script);
        }
    }
    
    /**
     * convert the QueryString to a Map<>
     * @param query
     * @return 
     */
    public static Map<String, String> queryToMap(String query){
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
     * Open properties and read from the properties file (FQ name)
     * @param prop
     * @param fq_filename
     * @return 
     */
    public static Properties getPropertiesFQ(Properties prop,String fq_filename) {
        InputStream input = null;
        try {
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
    
    /**
     * Get the current working directory
     * @return 
     */
    @SuppressWarnings("empty-statement")
    public static String getWorkingDirectory() {
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
     * read in file into a string
     * @param root
     * @param filename
     * @return 
    */
   @SuppressWarnings("empty-statement")
   public static String fileToString(String root,String filename)  {
       String contents = "";
       InputStream input = null;

       try {
           String current = new java.io.File( "." ).getCanonicalPath();
           String fq_filename = current + root + filename;
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
   
    // decode a URL-safe string
    public static String urlsafe_base64_decode(String encoded_str) {
       try {
           System.out.println("Decoding: " + encoded_str);
           String tmp = URLDecoder.decode(encoded_str,"UTF-8");
           return new String(Utils.base64_decode(tmp),"UTF-8");
       }
       catch (UnsupportedEncodingException ex) {
           System.out.println("EXCEPTION Decoding: " + encoded_str + " Message: " + ex.getMessage());
           return encoded_str;
       }
    }
    
    // Local Base64 Encode - Author/Credit: https://gist.github.com/EmilHernvall/953733
    public static String base64_encode(byte[] data)
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
    public static byte[] base64_decode(String data)
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
