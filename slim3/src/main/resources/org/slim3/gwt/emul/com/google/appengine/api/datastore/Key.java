package com.google.appengine.api.datastore;

import java.io.Serializable;

public class Key implements Serializable, Comparable {

    private static final long serialVersionUID = 1L;
    
    private String appId;    
    
    private long id;
    
    private transient AppIdNamespace appIdNamespace;
        
    private Key() {
    }
    
    public int compareTo(Object o) {
        return 0;
    }
}