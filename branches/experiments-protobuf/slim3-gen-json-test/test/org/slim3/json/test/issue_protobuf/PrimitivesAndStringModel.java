package org.slim3.json.test.issue_protobuf;

import org.slim3.datastore.Attribute;
import org.slim3.datastore.Model;

import com.google.appengine.api.datastore.Key;

@Model
public class PrimitivesAndStringModel {
    public static PrimitivesAndStringModel createModel(int baseValue){
        PrimitivesAndStringModel m = new PrimitivesAndStringModel();
        m.setBooleanValue(true);
        m.setDoubleValue(baseValue++);
        m.setFloatValue(baseValue++);
        m.setIntValue(baseValue++);
        m.setLongValue(baseValue++);
        m.setShortValue((short)baseValue++);
        m.setStringValue("hello");
        return m;
    }

    public boolean isBooleanValue() {
        return booleanValue;
    }
    public void setBooleanValue(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }
/*
    public char getCharValue() {

        return charValue;
    }
    public void setCharValue(char charValue) {
        this.charValue = charValue;
    }
*/
    public double getDoubleValue() {
        return doubleValue;
    }
    public void setDoubleValue(double doubleValue) {
        this.doubleValue = doubleValue;
    }
    public float getFloatValue() {
        return floatValue;
    }
    public void setFloatValue(float floatValue) {
        this.floatValue = floatValue;
    }
    public int getIntValue() {
        return intValue;
    }
    public void setIntValue(int intValue) {
        this.intValue = intValue;
    }
    public Key getKey() {
        return key;
    }
    public void setKey(Key key) {
        this.key = key;
    }
    public long getLongValue() {
        return longValue;
    }
    public void setLongValue(long longValue) {
        this.longValue = longValue;
    }
    public short getShortValue() {
        return shortValue;
    }
    public void setShortValue(short shortValue) {
        this.shortValue = shortValue;
    }
    public String getStringValue() {
        return stringValue;
    }
    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }
    @Attribute(primaryKey=true)
    private Key key;
    private boolean booleanValue;
//    private char charValue;
    private short shortValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private String stringValue;
}
