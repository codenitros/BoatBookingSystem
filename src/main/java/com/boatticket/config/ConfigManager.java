package com.boatticket.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Single source of truth for all app configuration — ticket prices, the dev-mode PDF toggle, and
 * the printer name override. Replaces the previous {@code PriceManager} + {@code AppConfig} pair,
 * which each loaded their own separate properties file; everything now lives in one bundled {@code
 * src/main/resources/Config.properties}.
 *
 * <p>On first run, that bundled default is copied to the external, user-editable location:
 *
 * <pre>
 *   &lt;user home&gt;/BoatTicketSystem/Config.properties
 * </pre>
 *
 * That external copy is what actually gets read from then on. To change a price, the dev.mode
 * toggle, printer name, or update timeout after deployment, edit that external file and restart the
 * app (or call {@link #reload()} if you want a running app to pick up an edit made while it's open)
 * — no rebuild required.
 */
public class ConfigManager {

  private static final String APP_DIR =
      System.getProperty("user.home") + File.separator + "BoatTicketSystem";

  private static final String CONFIG_FILE_PATH = APP_DIR + File.separator + "Config.properties";

  // Hardcoded fallbacks, only used if the properties file is somehow
  // missing/unreadable AND the bundled default resource can't be
  // found either. Normal operation always reads from the external
  // file above.
  private static final int DEFAULT_BOAT_RIDE_FEE_PER_HOUR = 1500;
  private static final int DEFAULT_BOAT_RIDE_FEE_2HOURS = 2500;
  private static final int DEFAULT_LIFE_JACKET_FEE = 10;
  private static final int DEFAULT_PARKING_FEE_CAR = 20;
  private static final int DEFAULT_PARKING_FEE_BUS = 50;
  private static final int DEFAULT_PARKING_FEE_TEMPOTRAVELLER = 40;
  private static final int DEFAULT_UPDATE_CONNECTION_TIMEOUT_SECONDS = 30;

  // Keep this false so a broken/missing config never accidentally
  // causes PDFs to be generated at a real deployed counter.
  private static final boolean DEFAULT_DEV_MODE = false;

  private static Properties cachedProperties;

  private ConfigManager() {}

  // ------------------------------------------------------------------
  // Prices
  // ------------------------------------------------------------------

  public static int getBoatRideFeePerHour() {
    return getInt("boat.ride.fee.per.hour", DEFAULT_BOAT_RIDE_FEE_PER_HOUR);
  }

  public static int getBoatRideFee2Hours() {
    return getInt("boat.ride.fee.2hours", DEFAULT_BOAT_RIDE_FEE_2HOURS);
  }

  public static int getLifeJacketFee() {
    return getInt("life.jacket.fee", DEFAULT_LIFE_JACKET_FEE);
  }

  public static int getParkingFeeCar() {
    return getInt("parking.fee.car", DEFAULT_PARKING_FEE_CAR);
  }

  public static int getParkingFeeBus() {
    return getInt("parking.fee.bus", DEFAULT_PARKING_FEE_BUS);
  }

  public static int getParkingFeeTempoTraveller() {
    return getInt("parking.fee.tempotraveller", DEFAULT_PARKING_FEE_TEMPOTRAVELLER);
  }

  /**
   * Maximum time to wait for a connection to GitHub's release/CDN endpoint when downloading an
   * application update.
   */
  public static int getUpdateConnectionTimeoutSeconds() {
    int timeout =
        getInt("update.connection.timeout.seconds", DEFAULT_UPDATE_CONNECTION_TIMEOUT_SECONDS);
    return timeout > 0 ? timeout : DEFAULT_UPDATE_CONNECTION_TIMEOUT_SECONDS;
  }

  // ------------------------------------------------------------------
  // Dev-mode toggle
  // ------------------------------------------------------------------

  /**
   * True during development: a reference PDF is generated alongside the thermal print for every
   * ticket, saved to Downloads/BoatTickets (not auto-opened) so you can check the layout.
   *
   * <p>False in production/deployment: only the thermal printer receives the ticket, nothing is
   * saved to disk.
   */
  public static boolean isDevMode() {
    String value = getProperties().getProperty("dev.mode");

    if (value == null || value.isBlank()) {
      return DEFAULT_DEV_MODE;
    }

    return Boolean.parseBoolean(value.trim());
  }

  // ------------------------------------------------------------------
  // Printer
  // ------------------------------------------------------------------

  /**
   * Optional exact printer name (as shown in Windows Settings > Printers & scanners) to always
   * print tickets to, regardless of whichever printer Windows currently has set as its default.
   *
   * <p>Empty string means "no override — use the system default printer", in which case {@link
   * com.boatticket.util.ThermalPrinterManager} will still refuse to use it if it's a
   * virtual/PDF-style printer.
   */
  public static String getPrinterName() {
    String value = getProperties().getProperty("printer.name");
    return value == null ? "" : value.trim();
  }

  // ------------------------------------------------------------------
  // Shared plumbing
  // ------------------------------------------------------------------

  /**
   * Returns the full path to the external, editable config file, so the UI can show it to the
   * operator if needed.
   */
  public static String getConfigFilePath() {
    return CONFIG_FILE_PATH;
  }

  /**
   * Forces the next lookup to re-read Config.properties from disk. Call this if the file was edited
   * while the app is still running and you want the change to take effect immediately.
   */
  public static synchronized void reload() {
    cachedProperties = null;
  }

  private static int getInt(String key, int fallback) {
    String value = getProperties().getProperty(key);

    if (value == null || value.isBlank()) {
      return fallback;
    }

    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static synchronized Properties getProperties() {
    if (cachedProperties == null) {
      cachedProperties = load();
    }

    return cachedProperties;
  }

  private static Properties load() {
    Properties props = new Properties();

    try {
      copyDefaultIfNeeded();

      try (InputStream inputStream = new FileInputStream(CONFIG_FILE_PATH)) {
        props.load(inputStream);
      }

      System.out.println("[ConfigManager] Reading config from: " + CONFIG_FILE_PATH);
      System.out.println(
          "[ConfigManager] dev.mode = "
              + props.getProperty("dev.mode")
              + ", printer.name = "
              + props.getProperty("printer.name"));
    } catch (IOException e) {
      e.printStackTrace();
      // Fall through — individual getters fall back to their
      // hardcoded defaults if a value couldn't be loaded at all.
    }

    return props;
  }

  private static void copyDefaultIfNeeded() throws IOException {
    Path configFilePath = Paths.get(CONFIG_FILE_PATH);

    Files.createDirectories(Paths.get(APP_DIR));

    if (Files.exists(configFilePath)) {
      return;
    }

    try (InputStream bundledDefaults =
        ConfigManager.class.getClassLoader().getResourceAsStream("Config.properties")) {
      if (bundledDefaults != null) {
        Files.copy(bundledDefaults, configFilePath);
      } else {
        writeHardcodedDefaults(configFilePath);
      }
    }
  }

  private static void writeHardcodedDefaults(Path configFilePath) throws IOException {
    Properties defaults = new Properties();
    defaults.setProperty("boat.ride.fee.per.hour", String.valueOf(DEFAULT_BOAT_RIDE_FEE_PER_HOUR));
    defaults.setProperty("boat.ride.fee.2hours", String.valueOf(DEFAULT_BOAT_RIDE_FEE_2HOURS));
    defaults.setProperty("life.jacket.fee", String.valueOf(DEFAULT_LIFE_JACKET_FEE));
    defaults.setProperty("parking.fee.car", String.valueOf(DEFAULT_PARKING_FEE_CAR));
    defaults.setProperty("parking.fee.bus", String.valueOf(DEFAULT_PARKING_FEE_BUS));
    defaults.setProperty(
        "parking.fee.tempotraveller", String.valueOf(DEFAULT_PARKING_FEE_TEMPOTRAVELLER));
    defaults.setProperty(
        "update.connection.timeout.seconds",
        String.valueOf(DEFAULT_UPDATE_CONNECTION_TIMEOUT_SECONDS));
    defaults.setProperty("dev.mode", String.valueOf(DEFAULT_DEV_MODE));
    defaults.setProperty("printer.name", "");

    try (OutputStream outputStream = new FileOutputStream(configFilePath.toFile())) {
      defaults.store(outputStream, "Boat Booking System - application configuration");
    }
  }
}
