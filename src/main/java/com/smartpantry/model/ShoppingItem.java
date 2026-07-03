package com.smartpantry.model;

import java.util.HashMap;
import java.util.Map;

public class ShoppingItem {

    private String id;
    private String name;
    private double quantity;
    private String unit;
    private boolean checked;
    private String userID;

    public ShoppingItem() {
    }

    public ShoppingItem(String name, double quantity, String unit, String userID) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.checked = false;
        this.userID = userID;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("quantity", quantity);
        map.put("unit", unit);
        map.put("checked", checked);
        map.put("userID", userID);
        return map;
    }

    @Override
    public String toString() {
        return (checked ? "✓ " : "") + name + " — " + quantity + " " + unit;
    }
}
