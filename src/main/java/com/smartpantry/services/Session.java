package com.smartpantry.services;

public class Session {

  private static Session instance;

  private String uid;
  private String email;

  private Session() {
  }

  public static synchronized Session getInstance() {
    if (instance == null)
      instance = new Session();
    return instance;
  }

  public void setUser(String uid, String email) {
    this.uid = uid;
    this.email = email;
  }

  public void clear() {
    uid = null;
    email = null;
  }

  public String getUid() {
    return uid;
  }

  public String getEmail() {
    return email;
  }

  public boolean isLoggedIn() {
    return uid != null;
  }
}
