package com.smartpantry;

import com.smartpantry.services.AuthService;

public class AuthTest {

  public static void main(String[] args) {
    if (args.length < 2) {
      System.out.println("Usage: pass an email and password as arguments");
      return;
    }
    String email = args[0];
    String password = args[1];
    AuthService authService = new AuthService();

    try {
      AuthService.AuthResult result = authService.signUp(email, password);
      System.out.println("Created new account:");
      printResult(result);
    } catch (AuthService.AuthException signUpError) {
      System.out.println("Sign-up failed (" + signUpError.getMessage() + "), trying sign-in...");
      try {
        AuthService.AuthResult result = authService.signIn(email, password);
        System.out.println("Signed in to existing account:");
        printResult(result);
      } catch (AuthService.AuthException signInError) {
        System.out.println("Sign-in also failed: " + signInError.getMessage());
      }
    }
  }

  private static void printResult(AuthService.AuthResult result) {
    System.out.println("  uid:   " + result.uid());
    System.out.println("  email: " + result.email());
  }
}
