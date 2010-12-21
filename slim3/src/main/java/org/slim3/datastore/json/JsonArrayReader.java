/*
 * Copyright 2004-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.slim3.datastore.json;

import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONObject;

/**
 * JSON array reader.
 * 
 * @author Takao Nakaguchi
 *
 * @since 1.0.6
 */
public class JsonArrayReader extends JsonReader {
    /**
     * The constructor.
     * 
     * @param array the JSON array
     * @param index the index
     * @param modelReader the model reader
     */
    public JsonArrayReader(JSONArray array, int index, ModelReader modelReader){
        super(modelReader);
        this.array = array;
        this.index = index;
    }

    /**
     * Returns the length.
     *
     * @return length
     */
    public int length(){
        return array.length();
    }

    /**
     * Sets the index.
     * 
     * @param index the index
     */
    public void setIndex(int index){
        this.index = index;
    }

    @Override
    public String read(){
        return array.optString(index, null);
    }

    @Override
    public String readProperty(String name){
        JSONObject o = array.optJSONObject(index);
        if(o == null) return null;
        return o.optString(name, null);
    }

    private JSONArray array;
    private int index;
}
