package com.boatticket.backup;

import com.boatticket.db.DatabaseManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Automatically produces a password-protected PDF backup of every ticket booked so far, once per
 * calendar day, the first time the app is opened that day.
 *
 * <p>Uses the exact same encrypted-PDF report format as the manual "Export Report" button ({@link
 * DatabaseManager#exportToPdfReport}) — same layout, same password. The only difference is this
 * runs silently in the background with no folder picker: it always saves to a fixed folder in the
 * user's Downloads, and it always backs up ALL tickets to date (not just a chosen date range), so
 * nothing is ever missed even if the app isn't opened every single day.
 */
public final class DailyBackupService {

  private static final Logger LOGGER = Logger.getLogger(DailyBackupService.class.getName());

  private static final Path BACKUP_DIR =
      Path.of(System.getProperty("user.home"), "Downloads", "BoatTicketBackups");

  private static final Path MARKER_FILE =
      Path.of(System.getProperty("user.home"), "BoatTicketSystem", "last_backup_date.txt");

  private static final DateTimeFormatter MARKER_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");

  // Used as the "from" date so every ticket ever booked is included, without
  // needing a separate "no lower bound" code path in exportToPdfReport.
  private static final LocalDate EARLIEST_POSSIBLE_DATE = LocalDate.of(2000, 1, 1);

  private DailyBackupService() {}

  /**
   * Fires the backup on a background thread if it hasn't already run today. Never touches the UI
   * and never throws back to the caller — failures are only logged, so a backup problem can never
   * block or slow down the app opening normally.
   */
  public static void runIfDueAsync() {
    CompletableFuture.runAsync(DailyBackupService::runIfDue)
        .exceptionally(
            failure -> {
              LOGGER.log(Level.WARNING, "Daily backup failed unexpectedly.", failure);
              return null;
            });
  }

  private static void runIfDue() {
    LocalDate today = LocalDate.now();

    if (alreadyRanToday(today)) {
      LOGGER.info(() -> "Daily backup already completed today (" + today + "); skipping.");
      return;
    }

    try {
      Files.createDirectories(BACKUP_DIR);

      File backupFile =
          DatabaseManager.getInstance()
              .exportToPdfReport(BACKUP_DIR.toString(), EARLIEST_POSSIBLE_DATE, today);

      LOGGER.info(() -> "Daily backup saved to: " + backupFile.getAbsolutePath());
      markCompleted(today);
    } catch (Exception exception) {
      LOGGER.log(Level.WARNING, "Daily backup failed.", exception);
    }
  }

  private static boolean alreadyRanToday(LocalDate today) {
    try {
      if (!Files.exists(MARKER_FILE)) {
        return false;
      }

      String lastRunText = Files.readString(MARKER_FILE).trim();
      return lastRunText.equals(today.format(MARKER_DATE_FORMAT));
    } catch (IOException exception) {
      LOGGER.log(
          Level.WARNING, "Could not read last backup marker; will attempt backup.", exception);
      return false;
    }
  }

  private static void markCompleted(LocalDate today) {
    try {
      Files.createDirectories(MARKER_FILE.getParent());
      Files.writeString(MARKER_FILE, today.format(MARKER_DATE_FORMAT));
    } catch (IOException exception) {
      LOGGER.log(Level.WARNING, "Could not write last backup marker.", exception);
    }
  }
}
