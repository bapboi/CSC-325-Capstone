package com.smartpantry.services;

import nu.pattern.OpenCV;

//inline opencv runner
public final class OpenCvSupport {

  private OpenCvSupport() {
  }

  public static void ensureLoaded() {
    OpenCV.loadLocally();
  }
}
