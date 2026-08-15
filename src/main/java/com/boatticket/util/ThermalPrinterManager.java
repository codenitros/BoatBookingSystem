package com.boatticket.util;

import com.boatticket.config.ConfigManager;
import com.boatticket.model.Ticket;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;

public class ThermalPrinterManager {

  private static final int LINE_WIDTH = 32; // 80mm thermal printer ~32 chars
  private static final String SEPARATOR = "================================";

  // Printer names that are virtual "print to file" destinations, not
  // real thermal/receipt printers. If the resolved printer's name
  // contains any of these (case-insensitive), we refuse to print to
  // it rather than silently generating a PDF/XPS/etc.
  private static final String[] VIRTUAL_PRINTER_KEYWORDS = {
    "microsoft print to pdf", "microsoft xps document writer", "onenote", "send to onenote", "fax"
  };

  /**
   * Resolves which printer to send the ticket to.
   *
   * <p>- If {@code printer.name} is set in Config.properties, that exact printer (matched
   * case-insensitively) is used. This is the recommended setup for a deployed ticket counter, so
   * printing never depends on whatever Windows happens to have set as its default printer. -
   * Otherwise, falls back to the system default printer — but refuses to use it if it's a known
   * virtual/PDF-style printer (e.g. "Microsoft Print to PDF"), since that indicates the Windows
   * default printer is misconfigured for this app rather than the app doing anything wrong.
   */
  public static PrintService resolvePrinter() throws PrintException {
    PrintService[] allPrinters = PrintServiceLookup.lookupPrintServices(null, null);
    String configuredName = ConfigManager.getPrinterName();

    if (!configuredName.isEmpty()) {
      for (PrintService printer : allPrinters) {
        if (printer.getName().equalsIgnoreCase(configuredName)) {
          return printer;
        }
      }
      throw new PrintException(
          "Configured printer \""
              + configuredName
              + "\" (printer.name in "
              + "Config.properties) was not found. Available printers: "
              + listPrinterNames(allPrinters));
    }

    PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
    if (defaultPrinter == null) {
      throw new PrintException("No default printer found on the system");
    }

    if (isVirtualPrinter(defaultPrinter.getName())) {
      throw new PrintException(
          "The Windows default printer is \""
              + defaultPrinter.getName()
              + "\", which creates a file instead of printing to a real device. "
              + "Either set that machine's default printer to the actual thermal "
              + "printer, or set printer.name=<exact printer name> in Config.properties. "
              + "Available printers: "
              + listPrinterNames(allPrinters));
    }

    return defaultPrinter;
  }

  /**
   * @deprecated use {@link #resolvePrinter()}, which also honours printer.name from
   *     Config.properties and rejects virtual/PDF printers. Kept only in case anything external
   *     still calls it directly.
   */
  @Deprecated
  public static PrintService getDefaultPrinter() throws PrintException {
    return resolvePrinter();
  }

  private static boolean isVirtualPrinter(String printerName) {
    if (printerName == null) {
      return false;
    }
    String lower = printerName.toLowerCase(Locale.ROOT);
    for (String keyword : VIRTUAL_PRINTER_KEYWORDS) {
      if (lower.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private static String listPrinterNames(PrintService[] printers) {
    if (printers == null || printers.length == 0) {
      return "(none found)";
    }
    return Stream.of(printers).map(PrintService::getName).collect(Collectors.joining(", "));
  }

  /** Print ticket to the resolved printer (see {@link #resolvePrinter()}). */
  public static String printTicket(Ticket ticket) throws PrintException, IOException {
    PrintService selectedPrinter = resolvePrinter();

    String content = formatTicketContent(ticket);
    byte[] bytes = content.getBytes("UTF-8");

    // Try different flavors to find one supported by the printer
    DocFlavor flavor = null;
    DocFlavor[] tryFlavors = {
      DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8,
      DocFlavor.BYTE_ARRAY.AUTOSENSE,
      DocFlavor.INPUT_STREAM.AUTOSENSE
    };

    for (DocFlavor tryFlavor : tryFlavors) {
      if (selectedPrinter.isDocFlavorSupported(tryFlavor)) {
        flavor = tryFlavor;
        break;
      }
    }

    if (flavor == null) {
      // Default to BYTE_ARRAY.AUTOSENSE if no supported flavor found
      flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
    }

    Doc doc = new SimpleDoc(bytes, flavor, null);
    DocPrintJob job = selectedPrinter.createPrintJob();

    PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
    attrs.add(new Copies(1));

    job.print(doc, attrs);
    return selectedPrinter.getName();
  }

  /** Print ticket to console for preview/debugging */
  public static void printTicketToConsole(Ticket ticket) {
    String content = formatTicketContent(ticket);
    System.out.println("\n" + "=".repeat(40));
    System.out.println("THERMAL PRINTER PREVIEW");
    System.out.println("=".repeat(40));
    System.out.println(content);
    System.out.println("=".repeat(40));
  }

  /** Format ticket content for 80mm thermal printer (colorless, optimized layout) */
  private static String formatTicketContent(Ticket ticket) {
    StringBuilder sb = new StringBuilder();

    // Header
    sb.append(centerText("SHARAVATI BACKWATER BOATING")).append("\n");
    sb.append(centerText("HONNAVAR, RECEIPT")).append("\n");
    sb.append(SEPARATOR).append("\n");
    sb.append("\n");

    // Ticket ID & Date
    // sb.append("*** TICKET ID: ").append(ticket.getTicketId()).append(" ***\n");
    sb.append(formatLine("DATE & TIME:", formatDateTime(ticket.getFormattedBookingTime())))
        .append("\n");
    sb.append("\n");

    // Customer Details
    //        sb.append("CUSTOMER DETAILS").append("\n");
    //        sb.append(SEPARATOR).append("\n");
    //        sb.append(formatLine("Name:", truncate(ticket.getCustomerName(), LINE_WIDTH -
    // 6))).append("\n");
    //        sb.append(formatLine("Contact:", ticket.getContactNumber())).append("\n");
    sb.append(formatLine("Persons:", String.valueOf(ticket.getNumberOfPeople()))).append("\n");
    sb.append("\n");

    // Driver & Boat Details
    if (ticket.getBoatOwner() != null) {
      sb.append("DRIVER & BOAT DETAILS").append("\n");
      sb.append(SEPARATOR).append("\n");
      sb.append(
              formatLine(
                  "Driver:", truncate(ticket.getBoatOwner().getDriverName(), LINE_WIDTH - 8)))
          .append("\n");
      sb.append(formatLine("Driver Number:", ticket.getBoatOwner().getContact())).append("\n");
      sb.append(formatLine("Boat HR:", ticket.getBoatOwner().getBoatHRNumber())).append("\n");
      sb.append(formatLine("Capacity:", ticket.getBoatOwner().getCapacity() + " persons"))
          .append("\n");
      sb.append("\n");
    }

    // Ride Details
    sb.append("RIDE DETAILS").append("\n");
    sb.append(SEPARATOR).append("\n");
    sb.append(formatLine("Duration:", "** " + ticket.getRideDurationInHour() + " HOUR(S) **"))
        .append("\n");

    if (ticket.isLifeJacketRequired()) {
      sb.append(formatLine("Life Jackets:", String.valueOf(ticket.getLifeJacketCount())))
          .append("\n");
    }

    if (ticket.isParkingRequired()) {
      String vehicleValue = "** " + ticket.getVehicleTypeDisplay();
      if (ticket.getVehicleNumber() != null && !ticket.getVehicleNumber().isBlank()) {
        vehicleValue += " | " + ticket.getVehicleNumber().toUpperCase();
      }
      vehicleValue += " **";
      sb.append(formatLine("Vehicle Parking:", vehicleValue)).append("\n");
    }
    sb.append("\n");

    // Fee Breakdown
    sb.append("FEE BREAKDOWN").append("\n");
    sb.append(SEPARATOR).append("\n");
    sb.append(formatLine("Boat Ride Fee:", "** Rs " + ticket.getBoatRideFee() + " **"))
        .append("\n");

    if (ticket.isLifeJacketRequired()) {
      sb.append(formatLine("Life Jacket Fee:", "Rs " + ticket.getLifeJacketFee())).append("\n");
    }

    if (ticket.isParkingRequired()) {
      sb.append(formatLine("Parking Fee:", "** Rs " + ticket.getParkingFee() + " **")).append("\n");
    }

    sb.append("\n");
    //        sb.append(centerText("*** AMOUNT TO DRIVER ***")).append("\n");
    //        sb.append(centerText("*** Rs " + ticket.getAmountToDriver() + " ***")).append("\n");
    //        sb.append(centerText("(Paid directly to driver)")).append("\n");
    //        sb.append("\n");

    // Counter Payable
    if (ticket.getCounterPayable() > 0) {
      sb.append(centerText("===== COUNTER PAYMENT =====")).append("\n");
      sb.append(centerText("*** PAYABLE AT COUNTER ***")).append("\n");
      sb.append(centerText("*** Rs " + ticket.getCounterPayable() + " ***")).append("\n");
      sb.append(centerText("(PARKING FEE ONLY)")).append("\n");
      sb.append("\n");
    }

    // Footer
    sb.append(SEPARATOR).append("\n");
    sb.append(centerText("Thank You!")).append("\n");
    sb.append(centerText("Safe & Happy Journey")).append("\n");
    sb.append("\n\n");

    // Multiple newlines to advance paper and cut
    for (int i = 0; i < 3; i++) {
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Formats a "label + value" line so the value's right edge lands on the same column every time,
   * no matter how long the label is -- e.g.:
   *
   * <pre>
   *   Driver:                Ravi Kumar
   *   Driver Number:         9900011001
   *   Boat HR:                    HR-001
   * </pre>
   *
   * If label + value can't fit on one line within {@link #LINE_WIDTH}, the value drops to its own
   * line, still right-aligned to the same column, instead of overflowing/wrapping unpredictably.
   */
  private static String formatLine(String label, String value) {
    if (value == null) {
      value = "";
    }

    int gap = LINE_WIDTH - label.length() - value.length();

    if (gap < 1) {
      return label + "\n" + rightAlign(value, LINE_WIDTH);
    }

    return label + " ".repeat(gap) + value;
  }

  /** Right-aligns text within the line width (pads with leading spaces). */
  private static String rightAlign(String text, int width) {
    if (text.length() >= width) {
      return text;
    }
    return " ".repeat(width - text.length()) + text;
  }

  /** Center text within the line width */
  private static String centerText(String text) {
    if (text.length() >= LINE_WIDTH) {
      return text.substring(0, LINE_WIDTH);
    }
    int padding = (LINE_WIDTH - text.length()) / 2;
    return " ".repeat(Math.max(0, padding)) + text;
  }

  /** Truncate text to fit within specified width */
  private static String truncate(String text, int maxLength) {
    if (text == null) return "";
    if (text.length() <= maxLength) return text;
    return text.substring(0, maxLength - 3) + "...";
  }

  /** Format date time string for thermal printer (condensed) */
  private static String formatDateTime(String formattedTime) {
    // Input format: "dd-MM-yyyy  HH:mm:ss"
    // Output: "dd/MM HH:mm"
    if (formattedTime == null || formattedTime.length() < 19) return formattedTime;
    String date = formattedTime.substring(0, 10).replace("-", "/");
    String time = formattedTime.substring(12, 17); // HH:mm
    return date + " " + time;
  }
}
