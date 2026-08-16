package com.boatticket.util;

import com.boatticket.config.ConfigManager;
import com.boatticket.model.Ticket;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Generates a printable PDF ticket, saves it to disk (Downloads folder), and opens it in the user's
 * default PDF viewer.
 */
public class PdfTicketManager {

  // 80mm receipt width in points (1mm ~= 2.83465pt), generous height for content.
  private static final float PAGE_WIDTH = 226f;
  private static final float PAGE_HEIGHT = 700f;

  private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
  private static final Font SUB_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8);
  private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
  private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
  private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f);
  private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

  /**
   * Generates the ticket PDF, saves it to the Downloads folder, and opens it.
   *
   * @return the File that was created and opened
   */
  public static File generateAndOpenTicketPdf(Ticket ticket) throws Exception {
    File pdfFile = generateTicketPdf(ticket);
    openPdf(pdfFile);
    return pdfFile;
  }

  /** Generates the ticket PDF and saves it to disk without opening it. */
  public static File generateTicketPdf(Ticket ticket) throws Exception {
    File outputDir = resolveDownloadsFolder();

    if (!outputDir.exists() && !outputDir.mkdirs()) {
      throw new IOException("Could not create output folder: " + outputDir.getAbsolutePath());
    }

    String fileName = "Ticket_" + ticket.getTicketId() + ".pdf";
    File pdfFile = new File(outputDir, fileName);

    Document document = new Document(new Rectangle(PAGE_WIDTH, PAGE_HEIGHT), 10, 10, 10, 10);
    FileOutputStream fos = new FileOutputStream(pdfFile);

    try {
      PdfWriter.getInstance(document, fos);
      document.open();
      addContent(document, ticket);
      document.close(); // writes the trailer/xref AND closes the underlying stream
    } catch (Exception e) {
      if (document.isOpen()) {
        document.close();
      }
      throw e;
    }

    return pdfFile;
  }

  /**
   * Opens the given PDF in the system's default PDF viewer. Not called by default anymore — kept
   * available if you ever want a manual "Open PDF" button.
   */
  public static void openPdf(File pdfFile) throws IOException {
    if (!Desktop.isDesktopSupported()) {
      throw new IOException(
          "Desktop is not supported on this system; cannot auto-open the PDF. "
              + "The file was saved to: "
              + pdfFile.getAbsolutePath());
    }

    Desktop desktop = Desktop.getDesktop();

    if (!desktop.isSupported(Desktop.Action.OPEN)) {
      throw new IOException(
          "Opening files is not supported on this system. "
              + "The file was saved to: "
              + pdfFile.getAbsolutePath());
    }

    desktop.open(pdfFile);
  }

  /**
   * Resolves a Downloads folder to save the ticket into, falling back to the user's home directory
   * if Downloads doesn't exist.
   */
  private static File resolveDownloadsFolder() {
    String userHome = System.getProperty("user.home");
    File downloads = new File(userHome, "Downloads");

    if (downloads.exists() && downloads.isDirectory()) {
      return new File(downloads, "BoatTickets" + File.separator + ConfigManager.getTenantId());
    }

    // Fallback: a dedicated folder in the user's home directory
    return new File(userHome, "BoatTickets" + File.separator + ConfigManager.getTenantId());
  }

  private static void addContent(Document document, Ticket ticket) throws DocumentException {
    Paragraph title = new Paragraph(ConfigManager.getReceiptHeader(), TITLE_FONT);
    title.setAlignment(Element.ALIGN_CENTER);
    document.add(title);

    Paragraph subtitle = new Paragraph(ConfigManager.getReceiptLocation() + ", RECEIPT", SUB_FONT);
    subtitle.setAlignment(Element.ALIGN_CENTER);
    subtitle.setSpacingAfter(4f);
    document.add(subtitle);

    addSeparator(document);

    document.add(kv("Ticket ID:", ticket.getTicketId()));
    document.add(kv("Date & Time:", ticket.getFormattedBookingTime()));
    document.add(kv("Persons:", String.valueOf(ticket.getNumberOfPeople())));
    addSpacer(document);

    if (ticket.getBoatOwner() != null) {
      addSectionHeader(document, "DRIVER & BOAT DETAILS");
      document.add(kv("Driver:", ticket.getBoatOwner().getDriverName()));
      document.add(kv("Driver Number:", ticket.getBoatOwner().getContact()));
      document.add(kv("Boat HR:", ticket.getBoatOwner().getBoatHRNumber()));
      document.add(kv("Capacity:", ticket.getBoatOwner().getCapacity() + " persons"));
      addSpacer(document);
    }

    addSectionHeader(document, "RIDE DETAILS");
    document.add(kv("Duration:", ticket.getRideDurationInHour() + " HOUR(S)"));

    if (ticket.isLifeJacketRequired()) {
      document.add(kv("Life Jackets:", String.valueOf(ticket.getLifeJacketCount())));
    }

    if (ticket.isParkingRequired()) {
      String parking = ticket.getVehicleTypeDisplay();
      if (ticket.getVehicleNumber() != null && !ticket.getVehicleNumber().isBlank()) {
        parking += " | " + ticket.getVehicleNumber().toUpperCase();
      }
      document.add(kv("Vehicle Parking:", parking));
    }
    addSpacer(document);

    addSectionHeader(document, "FEE BREAKDOWN");
    document.add(kv("Boat Ride Fee:", "Rs " + ticket.getBoatRideFee()));

    if (ticket.isLifeJacketRequired()) {
      document.add(kv("Life Jacket Fee:", "Rs " + ticket.getLifeJacketFee()));
    }

    if (ticket.isParkingRequired()) {
      document.add(kv("Parking Fee:", "Rs " + ticket.getParkingFee()));
    }
    addSpacer(document);

    if (ticket.getCounterPayable() > 0) {
      addSeparator(document);
      Paragraph counterLabel = new Paragraph("PAYABLE AT COUNTER", SECTION_FONT);
      counterLabel.setAlignment(Element.ALIGN_CENTER);
      document.add(counterLabel);

      Paragraph counterAmount = new Paragraph("Rs " + ticket.getCounterPayable(), TITLE_FONT);
      counterAmount.setAlignment(Element.ALIGN_CENTER);
      document.add(counterAmount);

      Paragraph counterNote = new Paragraph("(PARKING FEE ONLY)", FOOTER_FONT);
      counterNote.setAlignment(Element.ALIGN_CENTER);
      counterNote.setSpacingAfter(4f);
      document.add(counterNote);
      addSeparator(document);
    }

    addSpacer(document);
    Paragraph thanks = new Paragraph("Thank You!", SECTION_FONT);
    thanks.setAlignment(Element.ALIGN_CENTER);
    document.add(thanks);

    Paragraph wish = new Paragraph("Safe & Happy Journey", FOOTER_FONT);
    wish.setAlignment(Element.ALIGN_CENTER);
    document.add(wish);
  }

  private static void addSectionHeader(Document document, String text) throws DocumentException {
    Paragraph header = new Paragraph(text, SECTION_FONT);
    header.setSpacingBefore(2f);
    document.add(header);
    addSeparator(document);
  }

  private static void addSeparator(Document document) throws DocumentException {
    PdfPTable line = new PdfPTable(1);
    line.setWidthPercentage(100);
    PdfPCell cell = new PdfPCell();
    cell.setBorder(Rectangle.BOTTOM);
    cell.setBorderWidth(0.75f);
    cell.setFixedHeight(2f);
    line.addCell(cell);
    document.add(line);
  }

  private static void addSpacer(Document document) throws DocumentException {
    document.add(new Paragraph(" ", new Font(Font.FontFamily.HELVETICA, 4)));
  }

  private static Paragraph kv(String label, String value) {
    Paragraph p = new Paragraph();
    p.add(new Chunk(label + " ", BOLD_FONT));
    p.add(new Chunk(value == null ? "" : value, NORMAL_FONT));
    return p;
  }
}
