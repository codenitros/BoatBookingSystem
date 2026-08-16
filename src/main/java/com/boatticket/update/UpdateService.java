package com.boatticket.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the public release manifest and safely installs a newer Windows MSI after user approval.
 */
public final class UpdateService {
  private static final Logger LOGGER = Logger.getLogger(UpdateService.class.getName());
  private static final URI MANIFEST_URI =
      URI.create(
          "https://raw.githubusercontent.com/codenitros/BoatBookingSystemReleases/main/metadata/stable.json");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
  private static final Pattern VERSION_PATTERN =
      Pattern.compile("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
  private static final Pattern INSTALLER_URL_PATTERN =
      Pattern.compile("\\\"installerUrl\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
  private static final Pattern SHA256_PATTERN =
      Pattern.compile("\\\"sha256\\\"\\s*:\\s*\\\"([A-Fa-f0-9]{64})\\\"");

  private final HttpClient httpClient;

  public UpdateService() {
    this(
        HttpClient.newBuilder()
            // Release assets are served through GitHub's redirect/CDN path, which can take
            // longer to establish than the small metadata request.
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
  }

  UpdateService(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Runs without blocking the JavaFX application thread. Failures leave the application untouched.
   */
  public void checkForUpdateAsync(Consumer<UpdateInfo> onUpdateAvailable) {
    if (!isWindows()) {
      LOGGER.info("Skipping update check because the operating system is not Windows.");
      return;
    }

    LOGGER.info(() -> "Checking for updates. Installed version: " + currentVersion());
    CompletableFuture.supplyAsync(this::fetchUpdate)
        .thenAccept(
            update -> {
              if (update.isPresent()) {
                LOGGER.info(() -> "Update is available: " + update.get().version());
                onUpdateAvailable.accept(update.get());
              } else {
                LOGGER.info("No update is available.");
              }
            })
        .exceptionally(
            failure -> {
              LOGGER.log(Level.WARNING, "Update check failed unexpectedly.", failure);
              return null;
            });
  }

  /**
   * Downloads, verifies, and launches the MSI. The caller must exit the app immediately after
   * success.
   */
  public void downloadAndInstallAsync(
      UpdateInfo update, Runnable onInstallerStarted, Consumer<Exception> onFailure) {
    CompletableFuture.runAsync(
            () -> {
              try {
                Path installer = downloadAndVerify(update);
                launchInstallerAfterExit(installer);
                onInstallerStarted.run();
              } catch (Exception exception) {
                throw new UpdateException(exception);
              }
            })
        .exceptionally(
            failure -> {
              Throwable cause = failure.getCause() == null ? failure : failure.getCause();
              onFailure.accept(
                  cause instanceof Exception exception ? exception : new Exception(cause));
              return null;
            });
  }

  private Optional<UpdateInfo> fetchUpdate() {
    try {
      LOGGER.info(() -> "Requesting update metadata from " + MANIFEST_URI);
      HttpRequest request =
          HttpRequest.newBuilder(MANIFEST_URI).timeout(REQUEST_TIMEOUT).GET().build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      LOGGER.info(
          () ->
              "Update metadata response: HTTP "
                  + response.statusCode()
                  + ", body: "
                  + response.body());
      if (response.statusCode() != 200) {
        LOGGER.warning(() -> "Update metadata request returned HTTP " + response.statusCode());
        return Optional.empty();
      }

      UpdateInfo update = parseManifest(response.body());
      String installedVersion = currentVersion();
      boolean updateAvailable = isNewerVersion(update.version(), installedVersion);
      LOGGER.info(
          () ->
              "Parsed update metadata: latest version="
                  + update.version()
                  + ", installer="
                  + update.installerUrl()
                  + ", SHA-256="
                  + update.sha256()
                  + ". Installed version="
                  + installedVersion
                  + ". Update available="
                  + updateAvailable);
      return updateAvailable ? Optional.of(update) : Optional.empty();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      LOGGER.log(Level.WARNING, "Update metadata request was interrupted.", exception);
      return Optional.empty();
    } catch (Exception exception) {
      LOGGER.log(Level.WARNING, "Unable to check for updates.", exception);
      return Optional.empty();
    }
  }

  private Path downloadAndVerify(UpdateInfo update)
      throws IOException, InterruptedException, NoSuchAlgorithmException {
    URI installerUri = URI.create(update.installerUrl());
    if (!"https".equalsIgnoreCase(installerUri.getScheme())) {
      throw new IOException("Updater only accepts HTTPS installer URLs");
    }

    LOGGER.info(() -> "Downloading update installer from " + installerUri);
    HttpRequest request =
        HttpRequest.newBuilder(installerUri).timeout(Duration.ofMinutes(5)).GET().build();
    HttpResponse<InputStream> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    LOGGER.info(
        () ->
            "Installer download response: HTTP "
                + response.statusCode()
                + ", final URL: "
                + response.uri());
    if (response.statusCode() != 200) {
      throw new IOException("Installer download failed with HTTP " + response.statusCode());
    }

    Path installer = Files.createTempFile("BoatBookingSystem-" + update.version() + "-", ".msi");
    try (InputStream input = response.body()) {
      Files.copy(input, installer, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    if (!sha256(installer).equalsIgnoreCase(update.sha256())) {
      Files.deleteIfExists(installer);
      throw new IOException("Downloaded installer checksum does not match the release manifest");
    }
    LOGGER.info(() -> "Installer checksum verified: " + installer);
    return installer;
  }

  private static void launchInstallerAfterExit(Path installer) throws IOException {
    Path script = Files.createTempFile("BoatBookingSystem-update-", ".cmd");
    String command =
        "@echo off\r\n"
            + "timeout /t 2 /nobreak > NUL\r\n"
            + "msiexec.exe /i \""
            + installer.toAbsolutePath()
            + "\" /passive /norestart\r\n"
            + "del \"%~f0\"\r\n";
    Files.writeString(script, command, StandardCharsets.US_ASCII);
    new ProcessBuilder("cmd.exe", "/c", script.toString()).start();
  }

  private static UpdateInfo parseManifest(String manifest) throws IOException {
    String version = value(VERSION_PATTERN, manifest, "version");
    String installerUrl = value(INSTALLER_URL_PATTERN, manifest, "installerUrl");
    String sha256 = value(SHA256_PATTERN, manifest, "sha256");
    return new UpdateInfo(version, installerUrl, sha256);
  }

  private static String value(Pattern pattern, String manifest, String field) throws IOException {
    Matcher matcher = pattern.matcher(manifest);
    if (!matcher.find()) {
      throw new IOException("Release manifest is missing " + field);
    }
    return matcher.group(1);
  }

  static String currentVersion() {
    Properties properties = new Properties();
    try (InputStream input = UpdateService.class.getResourceAsStream("/version.properties")) {
      if (input != null) {
        properties.load(input);
      } else {
        LOGGER.warning("version.properties was not included in the application package.");
      }
    } catch (IOException exception) {
      LOGGER.log(Level.WARNING, "Cannot read the installed application version.", exception);
    }
    String version = properties.getProperty("application.version", "0.0.0");
    if (version.contains("${")) {
      LOGGER.warning(
          () ->
              "version.properties was not Maven-filtered (value: "
                  + version
                  + "). Treating the installed version as 0.0.0.");
      return "0.0.0";
    }
    return version;
  }

  static boolean isNewerVersion(String candidate, String installed) {
    int[] candidateParts = versionParts(candidate);
    int[] installedParts = versionParts(installed);
    for (int index = 0; index < candidateParts.length; index++) {
      if (candidateParts[index] != installedParts[index]) {
        return candidateParts[index] > installedParts[index];
      }
    }
    return false;
  }

  private static int[] versionParts(String version) {
    String normalized = version.startsWith("v") ? version.substring(1) : version;
    String[] strings = normalized.split("\\.");
    if (strings.length != 3) {
      throw new IllegalArgumentException("Version must be major.minor.patch: " + version);
    }
    return new int[] {
      Integer.parseInt(strings[0]), Integer.parseInt(strings[1]), Integer.parseInt(strings[2])
    };
  }

  private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(file)) {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = input.read(buffer)) != -1) {
        digest.update(buffer, 0, bytesRead);
      }
    }
    StringBuilder result = new StringBuilder();
    for (byte value : digest.digest()) {
      result.append(String.format("%02x", value));
    }
    return result.toString();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private static final class UpdateException extends RuntimeException {
    private UpdateException(Exception cause) {
      super(cause);
    }
  }
}
