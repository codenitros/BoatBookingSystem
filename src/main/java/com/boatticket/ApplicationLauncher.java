package com.boatticket;

import javafx.application.Application;

/**
 * Plain-Java entry point for packaged builds.
 *
 * <p>The JDK applies a special pre-launch check when the main class directly extends {@link
 * Application}. That check can incorrectly report missing JavaFX components for a self-contained
 * {@code jpackage} runtime. Starting JavaFX from a regular main class avoids the check while still
 * using the bundled JavaFX modules.
 */
public final class ApplicationLauncher {

  private ApplicationLauncher() {}

  public static void main(String[] args) {
    Application.launch(MainApp.class, args);
  }
}
