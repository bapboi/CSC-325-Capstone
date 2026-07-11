package com.smartpantry.model;

import com.google.cloud.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class Ingredient {

  // match fields to model diagram
  private String id;
  private String name;
  private double quantity;
  private String unit;
  private String category;
  private boolean detectedByAI;
  private String userID;
  private String photoFileName;
  private Timestamp createdAt;
  private Timestamp expirationDate;

  public Ingredient() {
  }

  public Ingredient(String name, double quantity, String unit, String category) {
    this.name = name;
    this.quantity = quantity;
    this.unit = unit;
    this.category = category;
    this.detectedByAI = false;
    this.createdAt = Timestamp.now();
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

  public boolean isDetectedByAI() {
    return detectedByAI;
  }

  public void setDetectedByAI(boolean detectedByAI) {
    this.detectedByAI = detectedByAI;
  }

  public String getUserID() {
    return userID;
  }

  public void setUserID(String userID) {
    this.userID = userID;
  }

  public String getPhotoFileName() {
    return photoFileName;
  }

  public void setPhotoFileName(String photoFileName) {
    this.photoFileName = photoFileName;
  }

  public Timestamp getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Timestamp createdAt) {
    this.createdAt = createdAt;
  }

  public Timestamp getExpirationDate() {
    return expirationDate;
  }

  public void setExpirationDate(Timestamp expirationDate) {
    this.expirationDate = expirationDate;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("quantity", quantity);
    map.put("unit", unit);
    map.put("category", category);
    map.put("detectedByAI", detectedByAI);
    map.put("userID", userID);
    map.put("createdAt", createdAt != null ? createdAt : Timestamp.now());
    if (expirationDate != null) {
      map.put("expirationDate", expirationDate);
    }
    if (photoFileName != null) {
      map.put("photoFileName", photoFileName);
    }
    return map;
  }

  @Override
  public String toString() {
    return name + " (" + quantity + " " + unit + ")";
  }
}
