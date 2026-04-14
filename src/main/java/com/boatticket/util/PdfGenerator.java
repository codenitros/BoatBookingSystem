package com.boatticket.util;

import com.boatticket.model.Ticket;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.FileOutputStream;
import java.io.IOException;

public class PdfGenerator {

    private static final BaseColor NAVY     = new BaseColor(10,  50, 100);
    private static final BaseColor SKY      = new BaseColor(30, 144, 255);
    private static final BaseColor LIGHT_BG = new BaseColor(235, 245, 255);
    private static final BaseColor DARK_ROW = new BaseColor(210, 230, 250);
    private static final BaseColor GREEN    = new BaseColor(34, 139,  34);
    private static final BaseColor ORANGE   = new BaseColor(210, 105,  30);
    private static final BaseColor WHITE    = BaseColor.WHITE;

    private static final Font TITLE_FONT     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, WHITE);
    private static final Font SUB_FONT       = FontFactory.getFont(FontFactory.HELVETICA,      10, WHITE);
    private static final Font HEADING_FONT   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, NAVY);
    private static final Font LABEL_FONT     = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, BaseColor.DARK_GRAY);
    private static final Font VALUE_FONT     = FontFactory.getFont(FontFactory.HELVETICA,        9, BaseColor.BLACK);
    private static final Font TICKET_ID_FONT = FontFactory.getFont(FontFactory.COURIER_BOLD,   11, SKY);
    private static final Font TOTAL_FONT     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, WHITE);
    private static final Font WARN_FONT      = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, ORANGE);
    private static final Font FOOTER_FONT    = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, BaseColor.GRAY);
    private static final Font DRIVER_FONT    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, new BaseColor(0, 80, 0));

    public static void generate(Ticket ticket, String outputPath) throws DocumentException, IOException {
        Document doc = new Document(PageSize.A5, 28, 28, 28, 28);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(outputPath));
        doc.open();

        // Header
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        PdfPCell hc = new PdfPCell();
        hc.setBackgroundColor(NAVY); hc.setPadding(12); hc.setBorder(Rectangle.NO_BORDER);
        Paragraph t = new Paragraph("SHARAVATI BACKWATER BOATING HONNAVAR TICKET", TITLE_FONT);
        t.setAlignment(Element.ALIGN_CENTER); hc.addElement(t);
        Paragraph sub = new Paragraph("1-Hour Scenic Boat Ride  |  Official Booking Receipt", SUB_FONT);
        sub.setAlignment(Element.ALIGN_CENTER); hc.addElement(sub);
        header.addCell(hc);
        doc.add(header);
        doc.add(Chunk.NEWLINE);

        // Ticket ID & Date
        PdfPTable idRow = new PdfPTable(2);
        idRow.setWidthPercentage(100); idRow.setWidths(new float[]{1f, 1f});
        addInfoCell(idRow, "Ticket ID",   ticket.getTicketId(),             TICKET_ID_FONT, LIGHT_BG);
        addInfoCell(idRow, "Date & Time", ticket.getFormattedBookingTime(), VALUE_FONT,     LIGHT_BG);
        doc.add(idRow);
        doc.add(Chunk.NEWLINE);

        // Customer
        addSectionHeader(doc, "CUSTOMER DETAILS");
        PdfPTable custT = new PdfPTable(2);
        custT.setWidthPercentage(100); custT.setWidths(new float[]{1f, 1f});
        addInfoCell(custT, "Customer Name",  ticket.getCustomerName(),  VALUE_FONT, WHITE);
        addInfoCell(custT, "Contact Number", ticket.getContactNumber(), VALUE_FONT, WHITE);
        doc.add(custT);
        doc.add(Chunk.NEWLINE);

        // Boat & Driver
        addSectionHeader(doc, "ASSIGNED BOAT & DRIVER");
        PdfPTable boatT = new PdfPTable(2);
        boatT.setWidthPercentage(100); boatT.setWidths(new float[]{1f, 1f});
        if (ticket.getBoatOwner() != null) {
            addInfoCell(boatT, "Driver Name",    ticket.getBoatOwner().getDriverName(), DRIVER_FONT, DARK_ROW);
            addInfoCell(boatT, "Boat Name",      ticket.getBoatOwner().getBoatName(),   DRIVER_FONT, DARK_ROW);
            addInfoCell(boatT, "Boat Number",    ticket.getBoatOwner().getBoatNumber(), VALUE_FONT,  WHITE);
            addInfoCell(boatT, "Capacity",       ticket.getBoatOwner().getCapacity() + " persons", VALUE_FONT, WHITE);
            addInfoCell(boatT, "Driver Contact", ticket.getBoatOwner().getContact(),   VALUE_FONT,  DARK_ROW);
            addInfoCell(boatT, "Booked By",      ticket.getBookedBy() != null ? ticket.getBookedBy() : "-", VALUE_FONT, DARK_ROW);
        }
        doc.add(boatT);
        doc.add(Chunk.NEWLINE);

        // Ride details
        addSectionHeader(doc, "RIDE DETAILS");
        PdfPTable rideT = new PdfPTable(2);
        rideT.setWidthPercentage(100); rideT.setWidths(new float[]{1f, 1f});
        addInfoCell(rideT, "Passengers",    String.valueOf(ticket.getNumberOfPeople()), VALUE_FONT, WHITE);
        addInfoCell(rideT, "Ride Duration", String.valueOf(ticket.getRideDurationInHour()), VALUE_FONT, WHITE);
        addInfoCell(rideT, "Life Jackets",
                ticket.isLifeJacketRequired() ? ticket.getLifeJacketCount() + " jacket(s)" : "Not Required",
                VALUE_FONT, DARK_ROW);
        String parkingStr = "Not Required";
        if (ticket.isParkingRequired()) {
            parkingStr = ticket.getVehicleTypeDisplay();
            if (ticket.getVehicleNumber() != null && !ticket.getVehicleNumber().isBlank())
                parkingStr += " | " + ticket.getVehicleNumber().toUpperCase();
        }
        addInfoCell(rideT, "Parking", parkingStr, VALUE_FONT, DARK_ROW);
        doc.add(rideT);
        doc.add(Chunk.NEWLINE);

        // Fees
        addSectionHeader(doc, "FEE BREAKDOWN");
        PdfPTable feeT = new PdfPTable(2);
        feeT.setWidthPercentage(100); feeT.setWidths(new float[]{2.2f, 1f});
        addFeeRow(feeT, "Boat Ride  (" + ticket.getRideDurationInHour() + " x Rs " + Ticket.BOAT_RIDE_FEE_PER_HOUR + ")",
                "Rs " + ticket.getBoatRideFee(), false);
        if (ticket.isLifeJacketRequired())
            addFeeRow(feeT, "Life Jacket  (" + ticket.getLifeJacketCount() + " x Rs " + Ticket.LIFE_JACKET_FEE + ")",
                    "Rs " + ticket.getLifeJacketFee(), true);
        if (ticket.isParkingRequired())
            addFeeRow(feeT, "Parking  (" + ticket.getVehicleTypeDisplay() + ")",
                    "Rs " + ticket.getParkingFee(), !ticket.isLifeJacketRequired());
        doc.add(feeT);

        // Driver notice
        PdfPTable noticeT = new PdfPTable(1);
        noticeT.setWidthPercentage(100);
        PdfPCell nc = new PdfPCell();
        nc.setBackgroundColor(new BaseColor(255, 243, 205)); nc.setPadding(7);
        nc.setBorder(Rectangle.BOX);
        nc.setBorderColor(new BaseColor(255, 160, 0)); nc.setBorderWidth(1.5f);
        nc.addElement(new Phrase(
                "IMPORTANT:  Rs " + ticket.getAmountToDriver() +
                " (Boat Ride" + (ticket.isLifeJacketRequired() ? " + Life Jacket" : "") +
                " charges) to be paid directly to the Boat Driver.", WARN_FONT));
        noticeT.addCell(nc);
        doc.add(noticeT);

        // Counter payable
        PdfPTable totalT = new PdfPTable(2);
        totalT.setWidthPercentage(100); totalT.setWidths(new float[]{2.2f, 1f});
        PdfPCell tl = new PdfPCell(new Phrase("PAYABLE AT COUNTER  (Parking Only)", TOTAL_FONT));
        tl.setBackgroundColor(NAVY); tl.setPadding(9); tl.setBorder(Rectangle.NO_BORDER);
        totalT.addCell(tl);
        PdfPCell tv = new PdfPCell(new Phrase("Rs " + ticket.getCounterPayable(), TOTAL_FONT));
        tv.setBackgroundColor(GREEN); tv.setPadding(9);
        tv.setHorizontalAlignment(Element.ALIGN_RIGHT); tv.setBorder(Rectangle.NO_BORDER);
        totalT.addCell(tv);
        doc.add(totalT);
        doc.add(Chunk.NEWLINE);

        // Barcode
        Barcode128 barcode = new Barcode128();
        barcode.setCode(ticket.getTicketId());
        barcode.setBarHeight(28f); barcode.setX(1.4f);
        PdfContentByte cb = writer.getDirectContent();
        Image barcodeImage = barcode.createImageWithBarcode(cb, NAVY, NAVY);
        barcodeImage.setAlignment(Element.ALIGN_CENTER);
        barcodeImage.scalePercent(90);
        doc.add(barcodeImage);
        doc.add(Chunk.NEWLINE);

        // Footer
        Paragraph footer = new Paragraph(
                "Thank you for choosing our Sharavati Boat Ride Service!\n" +
                "Please arrive 10 minutes before your scheduled ride.\n" +
                "Life jackets are mandatory for all passengers under 12 years.", FOOTER_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
        doc.close();
    }

    private static void addSectionHeader(Document doc, String text) throws DocumentException {
        PdfPTable t = new PdfPTable(1); t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase("  " + text, HEADING_FONT));
        c.setBackgroundColor(LIGHT_BG); c.setPadding(5);
        c.setBorder(Rectangle.BOTTOM); c.setBorderColor(SKY); c.setBorderWidth(2f);
        t.addCell(c); doc.add(t);
    }

    private static void addInfoCell(PdfPTable table, String label, String value, Font valueFont, BaseColor bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg); cell.setPadding(5);
        cell.setBorder(Rectangle.BOX); cell.setBorderColor(new BaseColor(200, 220, 240));
        cell.addElement(new Phrase(label, LABEL_FONT));
        cell.addElement(new Phrase(value, valueFont));
        table.addCell(cell);
    }

    private static void addFeeRow(PdfPTable table, String desc, String amount, boolean alt) {
        BaseColor bg = alt ? DARK_ROW : WHITE;
        PdfPCell d = new PdfPCell(new Phrase(desc, VALUE_FONT));
        d.setBackgroundColor(bg); d.setPadding(5);
        d.setBorder(Rectangle.BOX); d.setBorderColor(new BaseColor(200, 220, 240));
        table.addCell(d);
        PdfPCell a = new PdfPCell(new Phrase(amount, LABEL_FONT));
        a.setBackgroundColor(bg); a.setPadding(5);
        a.setHorizontalAlignment(Element.ALIGN_RIGHT);
        a.setBorder(Rectangle.BOX); a.setBorderColor(new BaseColor(200, 220, 240));
        table.addCell(a);
    }
}
