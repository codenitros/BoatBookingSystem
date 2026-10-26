package com.boatticket.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Controls, entirely through {@link ConfigManager#isConsoleEnabled()} (backed by each tenant's
 * external {@code Config.properties}), whether a visible log console appears at runtime.
 *
 * <p>The packaged app is a GUI-subsystem executable (no console window is ever created by Windows
 * itself — see {@code publish-release.yaml}, {@code --win-console} was intentionally removed), so
 * whether a console-like window shows up can no longer be decided by any OS/packaging setting. This
 * class fills that gap at the Java level instead, using its own independent {@code console}
 * property (separate from {@code dev.mode}, which only controls the reference-PDF feature):
 *
 * <ul>
 *   <li>{@code console=false} (default, and what every shipped tenant config uses): no window, no
 *       file, zero overhead. This is what every client/end user always sees.
 *   <li>{@code console=true}: all java.util.logging output is written to a log file AND a separate
 *       terminal window is spawned that live-tails that file, so it looks and behaves just like the
 *       old built-in console window did — except now it's controlled by editing one line in a
 *       properties file and restarting the app, not by how the installer was packaged.
 * </ul>
 */
public final class LoggingConfigurator {

  private static final String LOG_FILE_NAME = "app.log";
  private static volatile boolean configured = false;

  private LoggingConfigurator() {}

  /** Call once, as early as possible in application startup (before anything else logs). */
  public static synchronized void configure() {
    if (configured) {
      return;
    }

    configured = true;

    if (!ConfigManager.isConsoleEnabled()) {
      return;
    }

    try {
      Path logDir = Path.of(ConfigManager.getAppDirectory());
      Files.createDirectories(logDir);

      Path logFile = logDir.resolve(LOG_FILE_NAME);

      // append=true, single rotating-free file kept simple on purpose —
      // this is a developer convenience, not a production log pipeline.
      FileHandler fileHandler = new FileHandler(logFile.toString(), 5_000_000, 1, true);
      fileHandler.setFormatter(new SimpleFormatter());
      fileHandler.setLevel(Level.ALL);

      Logger rootLogger = Logger.getLogger("");
      rootLogger.addHandler(fileHandler);
      rootLogger.setLevel(Level.ALL);

      rootLogger.info(() -> "[CONSOLE] Logging to file: " + logFile.toAbsolutePath());

      openLiveLogWindow(logFile);
    } catch (IOException exception) {
      // Never let a logging setup failure block the app from starting.
      System.err.println(
          "[LoggingConfigurator] Could not set up dev-mode logging: " + exception.getMessage());
    }
  }

  /**
   * Spawns a separate, visible terminal window that live-tails the log file using PowerShell's
   * {@code Get-Content -Wait}, so it behaves just like the old built-in console did. Windows-only,
   * matching the rest of this Windows-only application; silently does nothing on any other OS.
   */
  private static void openLiveLogWindow(Path logFile) {
    if (!isWindows()) {
      return;
    }

    try {
      Path script = Files.createTempFile("BoatBookingSystem-devlog-", ".cmd");
      String command =
          "@echo off\r\n"
              + "title Boat Booking System - Console (console=true)\r\n"
              + "powershell -NoExit -Command \"Get-Content -Path '"
              + logFile.toAbsolutePath()
              + "' -Wait -Tail 200\"\r\n";
      Files.writeString(script, command, StandardCharsets.US_ASCII);

      new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", script.toString()).start();
    } catch (IOException exception) {
      System.err.println(
          "[LoggingConfigurator] Could not open the dev log window: " + exception.getMessage());
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}
