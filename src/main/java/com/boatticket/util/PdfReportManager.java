package com.boatticket.util;

import com.boatticket.model.TicketReport;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds a tabular ticket report as a genuinely password-protected PDF (real PDF encryption via
 * iText, not a cosmetic lock). The file cannot be opened in any PDF reader without the password;
 * the password itself is never shown anywhere in the application UI.
 */
public class PdfReportManager {

  private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
  private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11);
  private static final Font HEADER_FONT =
      FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BaseColor.WHITE);
  private static final Font CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
  private static final Font TOTAL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

  private static final BaseColor HEADER_BG = new BaseColor(30, 60, 114);
  private static final BaseColor ALT_ROW_BG = new BaseColor(240, 244, 248);

  /**
   * Generates the report PDF, encrypts it with the given password (as both the "open" password and
   * the "permissions" password), and writes it to destinationFile.
   */
  public static void generatePasswordProtectedReport(
      File destinationFile,
      List<TicketReport> rows,
      LocalDate fromDate,
      LocalDate toDate,
      int totalParkingFee,
      String openPassword)
      throws Exception {

    Document document = new Document(PageSize.A4.rotate(), 24, 24, 30, 30);
    FileOutputStream fos = new FileOutputStream(destinationFile);

    try {
      PdfWriter writer = PdfWriter.getInstance(document, fos);

      byte[] passwordBytes = openPassword.getBytes(StandardCharsets.UTF_8);

      // Same password is required both to OPEN the file and to change
      // permissions — so there is no way to view the content without
      // it, in any PDF reader.
      writer.setEncryption(
          passwordBytes,
          passwordBytes,
          PdfWriter.ALLOW_PRINTING | PdfWriter.ALLOW_COPY,
          PdfWriter.ENCRYPTION_AES_128);
      document.open();
      addContent(document, rows, fromDate, toDate, totalParkingFee);
      document.close();
    } catch (Exception e) {
      if (document.isOpen()) {
        document.close();
      }
      throw e;
    }
  }

  private static void addContent(
      Document document,
      List<TicketReport> rows,
      LocalDate fromDate,
      LocalDate toDate,
      int totalParkingFee)
      throws DocumentException {

    Paragraph title = new Paragraph("SHARAVATI BACKWATER BOATING - HONNAVAR", TITLE_FONT);
    title.setAlignment(Element.ALIGN_CENTER);
    document.add(title);

    Paragraph subtitle = new Paragraph("Ticket Report", SUBTITLE_FONT);
    subtitle.setAlignment(Element.ALIGN_CENTER);
    subtitle.setSpacingAfter(2f);
    document.add(subtitle);

    DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    Paragraph range =
        new Paragraph(
            "Date Range: "
                + fromDate.format(displayFormat)
                + "  to  "
                + toDate.format(displayFormat),
            SUBTITLE_FONT);
    range.setAlignment(Element.ALIGN_CENTER);
    range.setSpacingAfter(12f);
    document.add(range);

    String[] headers = {
      "Ticket ID",
      "Booking Time",
      "Customer Name",
      "Contact",
      "People",
      "Duration (Hrs)",
      "Total Fee",
      "Driver Name",
      "Parking Fee"
    };

    float[] columnWidths = {11f, 14f, 14f, 10f, 7f, 9f, 9f, 14f, 9f};
    PdfPTable table = new PdfPTable(columnWidths);
    table.setWidthPercentage(100);
    table.setHeaderRows(1);

    for (String header : headers) {
      PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
      cell.setBackgroundColor(HEADER_BG);
      cell.setPadding(5f);
      cell.setHorizontalAlignment(Element.ALIGN_CENTER);
      table.addCell(cell);
    }

    int rowIndex = 0;

    for (TicketReport row : rows) {
      BaseColor rowColor = (rowIndex % 2 == 0) ? BaseColor.WHITE : ALT_ROW_BG;

      addCell(table, row.getTicketId(), rowColor);
      addCell(table, row.getBookingTime(), rowColor);
      addCell(table, row.getCustomerName(), rowColor);
      addCell(table, row.getContactNumber(), rowColor);
      addCell(table, String.valueOf(row.getNumberOfPeople()), rowColor);
      addCell(table, String.valueOf(row.getRideDurationInHour()), rowColor);
      addCell(table, "Rs " + row.getTotalFee(), rowColor);
      addCell(table, row.getDriverName() != null ? row.getDriverName() : "-", rowColor);
      addCell(table, "Rs " + row.getParkingFee(), rowColor);

      rowIndex++;
    }

    if (rows.isEmpty()) {
      PdfPCell emptyCell =
          new PdfPCell(new Phrase("No tickets found in the selected date range.", CELL_FONT));
      emptyCell.setColspan(headers.length);
      emptyCell.setPadding(8f);
      emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
      table.addCell(emptyCell);
    }

    document.add(table);

    int totalPeople = 0;
    for (TicketReport row : rows) {
      totalPeople += row.getNumberOfPeople();
    }

    Paragraph totalPeopleLine = new Paragraph("Total Number of People: " + totalPeople, TOTAL_FONT);
    totalPeopleLine.setSpacingBefore(10f);
    document.add(totalPeopleLine);

    Paragraph totalLine =
        new Paragraph(
            "Total Money Collected at Counter (Parking Fee): Rs " + totalParkingFee, TOTAL_FONT);
    totalLine.setSpacingBefore(4f);
    document.add(totalLine);

    Paragraph ticketCount = new Paragraph("Total Tickets in Range: " + rows.size(), TOTAL_FONT);
    ticketCount.setSpacingBefore(4f);
    document.add(ticketCount);
  }

  private static void addCell(PdfPTable table, String text, BaseColor background) {
    PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, CELL_FONT));
    cell.setBackgroundColor(background);
    cell.setPadding(4f);
    table.addCell(cell);
  }
}
