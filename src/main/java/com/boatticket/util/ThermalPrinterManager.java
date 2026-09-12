package com.boatticket.util;

import com.boatticket.config.ConfigManager;
import com.boatticket.model.Ticket;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;

/** Generates and sends full-width raw ESC/POS tickets to a thermal printer. */
public class ThermalPrinterManager {
  private static final int RP4230_PRINTABLE_WIDTH_DOTS = 832;
  private static final int RP4230_DPI = 203;
  private static final int ROLL_WIDTH_DOTS = 640;
  private static final int NORMAL_FONT_CHARACTER_WIDTH_DOTS = 12;
  // The 80 mm roll has a 10 mm (80-dot) unprintable area on the left, leaving 70 mm (560 dots)
  // for content. Raw ESC/POS bypasses the Windows driver setting, so the printer margin is set
  // explicitly below.
  private static final int CONTENT_CHARACTERS_PER_LINE = 52;
  private static final int LOGO_HEIGHT_DOTS = RP4230_DPI * 10 / 25;
  private static final String LOGO_RESOURCE = "/com/boatticket/images/karnataka-tourism-logo.png";
  private static final String[] VIRTUAL_PRINTER_KEYWORDS = {
    "microsoft print to pdf", "microsoft xps document writer", "onenote", "send to onenote", "fax"
  };

  public static PrintService resolvePrinter() throws PrintException {
    PrintService[] allPrinters = PrintServiceLookup.lookupPrintServices(null, null);
    String configuredName = ConfigManager.getPrinterName();
    if (!configuredName.isEmpty()) {
      for (PrintService printer : allPrinters) {
        if (printer.getName().equalsIgnoreCase(configuredName)) return printer;
      }
      throw new PrintException(
          "Configured printer \""
              + configuredName
              + "\" was not found. Available printers: "
              + listPrinterNames(allPrinters));
    }
    PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
    if (defaultPrinter == null) throw new PrintException("No default printer found on the system");
    if (isVirtualPrinter(defaultPrinter.getName())) {
      throw new PrintException(
          "The Windows default printer is \""
              + defaultPrinter.getName()
              + "\", which creates a file instead of printing to a real device. Available printers: "
              + listPrinterNames(allPrinters));
    }
    return defaultPrinter;
  }

  /**
   * @deprecated Use {@link #resolvePrinter()}.
   */
  @Deprecated
  public static PrintService getDefaultPrinter() throws PrintException {
    return resolvePrinter();
  }

  /** Sends a raw ESC/POS ticket, including a centered 10 mm logo and full cut. */
  public static String printTicket(Ticket ticket) throws PrintException, IOException {
    PrintService selectedPrinter = resolvePrinter();
    DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
    if (!selectedPrinter.isDocFlavorSupported(flavor)) {
      throw new PrintException(
          "Printer \""
              + selectedPrinter.getName()
              + "\" does not accept raw ESC/POS data. Install/select its ESC/POS-compatible driver.");
    }
    Doc doc = new SimpleDoc(formatTicketBytes(ticket), flavor, null);
    DocPrintJob job = selectedPrinter.createPrintJob();
    PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
    attrs.add(new Copies(1));
    attrs.add(new JobName("Boat ticket " + ticket.getTicketId(), Locale.ROOT));
    job.print(doc, attrs);
    return selectedPrinter.getName();
  }

  /** Prints the readable ticket text to the console; image/control bytes are excluded. */
  public static void printTicketToConsole(Ticket ticket) {
    System.out.println("\n" + "=".repeat(40));
    System.out.println("THERMAL PRINTER PREVIEW");
    System.out.println("=".repeat(40));
    System.out.println(formatTicketContent(ticket));
    System.out.println("=".repeat(40));
  }

  private static byte[] formatTicketBytes(Ticket ticket) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int printableWidth = configuredPrintableWidth();
    int leftMargin = configuredLeftMargin();
    output.write(new byte[] {0x1B, 0x40}); // ESC @: initialize
    output.write(new byte[] {0x1D, 0x4C, (byte) leftMargin, (byte) (leftMargin >> 8)}); // GS L
    output.write(
        new byte[] {0x1D, 0x57, (byte) printableWidth, (byte) (printableWidth >> 8)}); // GS W
    writeCenteredLogo(output, printableWidth);
    // Position zero is now the configured GS L left margin, so all text starts after the 10 mm
    // unprintable area.
    output.write(new byte[] {0x1B, 0x24, 0x00, 0x00}); // ESC $ 0: left margin
    output.write(new byte[] {0x1B, 0x61, 0x00}); // ESC a 0: left justification
    output.write(formatTicketContent(ticket).getBytes(StandardCharsets.US_ASCII));
    output.write(new byte[] {0x1D, 0x56, 0x42, 0x00}); // GS V B 0: feed then full cut
    return output.toByteArray();
  }

  private static void writeCenteredLogo(ByteArrayOutputStream output, int printableWidth)
      throws IOException {
    BufferedImage logo = loadAndScaleLogo(printableWidth);
    int rasterWidth = Math.max(8, printableWidth - (printableWidth % 8)); // must be multiple of 8
    int bytesPerRow = rasterWidth / 8;
    int logoLeft = Math.max(0, (rasterWidth - logo.getWidth()) / 2);
    output.write(
        new byte[] {
          0x1D,
          0x76,
          0x30,
          0x00,
          (byte) bytesPerRow,
          (byte) (bytesPerRow >> 8),
          (byte) logo.getHeight(),
          (byte) (logo.getHeight() >> 8)
        }); // GS v 0
    for (int y = 0; y < logo.getHeight(); y++) {
      for (int byteIndex = 0; byteIndex < bytesPerRow; byteIndex++) {
        int bits = 0;
        for (int bit = 0; bit < 8; bit++) {
          int rasterX = byteIndex * 8 + bit;
          int logoX = rasterX - logoLeft;
          if (logoX >= 0 && logoX < logo.getWidth() && isDark(logo.getRGB(logoX, y))) {
            bits |= 0x80 >> bit;
          }
        }
        output.write(bits);
      }
    }
    output.write('\n');
  }

  private static BufferedImage loadAndScaleLogo(int printableWidth) throws IOException {
    try (InputStream input = ThermalPrinterManager.class.getResourceAsStream(LOGO_RESOURCE)) {
      if (input == null)
        throw new IOException("Bundled Karnataka Tourism logo is missing: " + LOGO_RESOURCE);
      BufferedImage source = ImageIO.read(input);
      if (source == null) throw new IOException("Bundled Karnataka Tourism logo could not be read");
      BufferedImage cropped = cropWhiteMargin(source);
      int targetWidth =
          Math.min(printableWidth, cropped.getWidth() * LOGO_HEIGHT_DOTS / cropped.getHeight());
      BufferedImage scaled =
          new BufferedImage(targetWidth, LOGO_HEIGHT_DOTS, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = scaled.createGraphics();
      graphics.drawImage(
          cropped.getScaledInstance(targetWidth, LOGO_HEIGHT_DOTS, Image.SCALE_SMOOTH), 0, 0, null);
      graphics.dispose();
      return scaled;
    }
  }

  private static BufferedImage cropWhiteMargin(BufferedImage source) {
    int minX = source.getWidth(), minY = source.getHeight(), maxX = -1, maxY = -1;
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        if (isDark(source.getRGB(x, y))) {
          minX = Math.min(minX, x);
          minY = Math.min(minY, y);
          maxX = Math.max(maxX, x);
          maxY = Math.max(maxY, y);
        }
      }
    }
    return maxX < minX ? source : source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
  }

  private static boolean isDark(int rgb) {
    int red = (rgb >> 16) & 0xFF, green = (rgb >> 8) & 0xFF, blue = rgb & 0xFF;
    return red * 299 + green * 587 + blue * 114 < 220_000;
  }

  private static String formatTicketContent(Ticket ticket) {
    int lineWidth = configuredCharactersPerLine();
    StringBuilder sb = new StringBuilder();
    sb.append(bold(centerText(ConfigManager.getReceiptHeader(), lineWidth))).append("\n");
    sb.append(bold(centerText(ConfigManager.getReceiptLocation() + ", RECEIPT", lineWidth)))
        .append("\n");
    sb.append(rule(lineWidth));
    sb.append(formatLine("Ticket ID:", ticket.getTicketId(), lineWidth)).append("\n");
    sb.append(
            formatLine("Date & Time:", formatDateTime(ticket.getFormattedBookingTime()), lineWidth))
        .append("\n");
    sb.append(formatLine("People:", String.valueOf(ticket.getNumberOfPeople()), lineWidth))
        .append("\n\n");
    if (ticket.getBoatOwner() != null) {
      sb.append(bold("DRIVER & BOAT DETAILS")).append("\n").append(rule(lineWidth));
      sb.append(
              formatLine("Driver SL No:", String.valueOf(ticket.getBoatOwner().getId()), lineWidth))
          .append("\n");
      sb.append(formatLine("Driver:", ticket.getBoatOwner().getDriverName(), lineWidth))
          .append("\n");
      sb.append(formatLine("Driver Phone Number:", ticket.getBoatOwner().getContact(), lineWidth))
          .append("\n");
      sb.append(formatLine("Boat HR Number:", ticket.getBoatOwner().getBoatHRNumber(), lineWidth))
          .append("\n");
      sb.append(formatLine("Jetty:", ticket.getBoatOwner().getJetty(), lineWidth)).append("\n");
      sb.append(formatLine("Capacity:", ticket.getBoatOwner().getCapacity() + " people", lineWidth))
          .append("\n\n");
    }
    sb.append(bold("RIDE DETAILS")).append("\n").append(rule(lineWidth));
    sb.append(
            formatLineWithBoldValue(
                "Duration:", ticket.getRideDurationInHour() + " Hour(s)", lineWidth))
        .append("\n");
    if (ticket.isLifeJacketRequired())
      sb.append(formatLine("Life Jackets:", String.valueOf(ticket.getLifeJacketCount()), lineWidth))
          .append("\n");
    if (ticket.isParkingRequired()) {
      String vehicle = ticket.getVehicleTypeDisplay();
      if (ticket.getVehicleNumber() != null && !ticket.getVehicleNumber().isBlank())
        vehicle += " | " + ticket.getVehicleNumber().toUpperCase(Locale.ROOT);
      sb.append(formatLineWithBoldValue("Vehicle Parking:", vehicle, lineWidth)).append("\n");
    }
    sb.append("\n").append(bold("FEE BREAKDOWN")).append("\n").append(rule(lineWidth));
    sb.append(formatLineWithBoldValue("Boat Ride Fee:", "Rs " + ticket.getBoatRideFee(), lineWidth))
        .append("\n");
    if (ticket.isLifeJacketRequired())
      sb.append(formatLine("Life Jacket Fee:", "Rs " + ticket.getLifeJacketFee(), lineWidth))
          .append("\n");
    if (ticket.isParkingRequired())
      sb.append(formatLineWithBoldValue("Parking Fee:", "Rs " + ticket.getParkingFee(), lineWidth))
          .append("\n\n");
    if (ticket.getCounterPayable() > 0) {
      sb.append(bold(centerText("PAYABLE AT COUNTER", lineWidth))).append("\n");
      sb.append(bold(centerText("Rs " + ticket.getCounterPayable(), lineWidth))).append("\n");
      sb.append(centerText("(PARKING FEE ONLY)", lineWidth)).append("\n");
    }
    return sb.append(rule(lineWidth))
        .append(bold(centerText("Thank You!", lineWidth)))
        .append("\n")
        .append(bold(centerText("Safe & Happy Boating", lineWidth)))
        .append("\n")
        .toString();
  }

  /** ESC/POS underline mode makes one continuous rule, unlike a row of '=' glyphs. */
  private static String rule(int width) {
    return "\u001B-\u0001" + " ".repeat(width) + "\u001B-\u0000\n";
  }

  private static String bold(String text) {
    return "\u001BE\u0001" + text + "\u001BE\u0000";
  }

  private static String formatLine(String label, String value, int width) {
    String safeValue = value == null ? "" : value;
    int gap = width - label.length() - safeValue.length();
    return gap < 1
        ? label + "\n" + rightAlign(safeValue, width)
        : label + " ".repeat(gap) + safeValue;
  }

  private static String formatLineWithBoldValue(String label, String value, int width) {
    String safeValue = value == null ? "" : value;
    int gap = width - label.length() - safeValue.length();
    return gap < 1
        ? label + "\n" + " ".repeat(Math.max(0, width - safeValue.length())) + bold(safeValue)
        : label + " ".repeat(gap) + bold(safeValue);
  }

  private static String rightAlign(String text, int width) {
    return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
  }

  private static String centerText(String text, int width) {
    return text.length() >= width
        ? text.substring(0, width)
        : " ".repeat((width - text.length()) / 2) + text;
  }

  private static String formatDateTime(String time) {
    return time == null || time.length() < 19
        ? time
        : time.substring(0, 10).replace("-", "/") + " " + time.substring(12, 17);
  }

  private static int configuredPrintableWidth() {
    int width = ConfigManager.getPrinterWidthDots();
    int availableWidth = ROLL_WIDTH_DOTS - configuredLeftMargin();
    if (width <= 0) return availableWidth;
    return Math.min(width, availableWidth);
  }

  private static int configuredLeftMargin() {
    int margin = ConfigManager.getPrinterLeftMarginDots();
    return margin >= 0 && margin < ROLL_WIDTH_DOTS ? margin : 0;
  }

  private static int configuredCharactersPerLine() {
    int width = ConfigManager.getPrinterCharactersPerLine();
    int maximumCharacters = configuredPrintableWidth() / NORMAL_FONT_CHARACTER_WIDTH_DOTS;
    if (width <= 0) return maximumCharacters;
    return Math.min(width, Math.min(CONTENT_CHARACTERS_PER_LINE, maximumCharacters));
  }

  private static boolean isVirtualPrinter(String name) {
    if (name == null) return false;
    String lower = name.toLowerCase(Locale.ROOT);
    for (String keyword : VIRTUAL_PRINTER_KEYWORDS) if (lower.contains(keyword)) return true;
    return false;
  }

  private static String listPrinterNames(PrintService[] printers) {
    return printers == null || printers.length == 0
        ? "(none found)"
        : Stream.of(printers).map(PrintService::getName).collect(Collectors.joining(", "));
  }
}
