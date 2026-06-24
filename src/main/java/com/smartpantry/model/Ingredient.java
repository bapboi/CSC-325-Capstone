package com.smartpantry.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

// pantry ingredient structure, need to merge and take skeleton from zeshan
public class Ingredient {

  private String id; // Firestore document id, not stored as a field inside the document
  private String name;
  private double quantity;
  private String unit;
  private String category;
  private String addedDate; // ISO-8601, e.g. 2026-06-17

  // constructor for deserialization
  public Ingredient() {
  }

  public Ingredient(String name, double quantity, String unit, String category) {
    this.name = name;
    this.quantity = quantity;
    this.unit = unit;
    this.category = category;
    this.addedDate = LocalDate.now().toString();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public double getQuantity() {
    return quantity;
  }

  public void setQuantity(double quantity) {
    this.quantity = quantity;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getAddedDate() {
    return addedDate;
  }

  public void setAddedDate(String addedDate) {
    this.addedDate = addedDate;
  }

  // returns map of ingredients for firestore storage
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("quantity", quantity);
    map.put("unit", unit);
    map.put("category", category);
    map.put("addedDate", addedDate);
    return map;
  }

  @Override
  public String toString() {
    return name + " (" + quantity + " " + unit + ")";
  }
}
