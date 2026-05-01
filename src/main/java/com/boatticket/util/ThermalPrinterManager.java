package com.boatticket.util;

import com.boatticket.model.Ticket;
import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import java.io.IOException;

public class ThermalPrinterManager {

    private static final int LINE_WIDTH = 32; // 80mm thermal printer ~32 chars
    private static final String SEPARATOR = "================================";

    /**
     * Get system default printer
     */
    public static PrintService getDefaultPrinter() throws PrintException {
        PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultPrinter == null) {
            throw new PrintException("No default printer found on the system");
        }
        return defaultPrinter;
    }


    /**
     * Print ticket to system default printer
     */
    public static String printTicket(Ticket ticket) throws PrintException, IOException {
        PrintService selectedPrinter = getDefaultPrinter();

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


    /**
     * Print ticket to console for preview/debugging
     */
    public static void printTicketToConsole(Ticket ticket) {
        String content = formatTicketContent(ticket);
        System.out.println("\n" + "=".repeat(40));
        System.out.println("THERMAL PRINTER PREVIEW");
        System.out.println("=".repeat(40));
        System.out.println(content);
        System.out.println("=".repeat(40));
    }

    /**
     * Format ticket content for 80mm thermal printer (colorless, optimized layout)
     */
    private static String formatTicketContent(Ticket ticket) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(centerText("SHARAVATI BACKWATER")).append("\n");
        sb.append(centerText("BOATING HONNAVAR")).append("\n");
        sb.append(centerText("TICKET RECEIPT")).append("\n");
        sb.append(SEPARATOR).append("\n");
        sb.append("\n");

        // Ticket ID & Date
        sb.append("*** TICKET ID: ").append(ticket.getTicketId()).append(" ***\n");
        sb.append("DATE & TIME: ").append(formatDateTime(ticket.getFormattedBookingTime())).append("\n");
        sb.append("\n");

        // Customer Details
        sb.append("CUSTOMER DETAILS").append("\n");
        sb.append(SEPARATOR).append("\n");
        sb.append("Name: ").append(truncate(ticket.getCustomerName(), LINE_WIDTH - 6)).append("\n");
        sb.append("Contact: ").append(ticket.getContactNumber()).append("\n");
        sb.append("Persons: ").append(ticket.getNumberOfPeople()).append("\n");
        sb.append("\n");

        // Driver & Boat Details
        if (ticket.getBoatOwner() != null) {
            sb.append("DRIVER & BOAT DETAILS").append("\n");
            sb.append(SEPARATOR).append("\n");
            sb.append("Driver: ").append(truncate(ticket.getBoatOwner().getDriverName(), LINE_WIDTH - 8)).append("\n");
            sb.append("Boat HR: ").append(ticket.getBoatOwner().getBoatHRNumber()).append("\n");
            sb.append("Capacity: ").append(ticket.getBoatOwner().getCapacity()).append(" persons\n");
            sb.append("\n");
        }

        // Ride Details
        sb.append("RIDE DETAILS").append("\n");
        sb.append(SEPARATOR).append("\n");
        sb.append("Duration: ** ").append(ticket.getRideDurationInHour()).append(" HOUR(S) **\n");

        if (ticket.isLifeJacketRequired()) {
            sb.append("Life Jackets: ").append(ticket.getLifeJacketCount()).append("\n");
        }

        if (ticket.isParkingRequired()) {
            sb.append("Vehicle Parking: ** ").append(ticket.getVehicleTypeDisplay());
            if (ticket.getVehicleNumber() != null && !ticket.getVehicleNumber().isBlank()) {
                sb.append(" | ").append(ticket.getVehicleNumber().toUpperCase());
            }
            sb.append(" **\n");
        }
        sb.append("\n");

        // Fee Breakdown
        sb.append("FEE BREAKDOWN").append("\n");
        sb.append(SEPARATOR).append("\n");
        sb.append("Boat Ride Fee: ** Rs ").append(ticket.getBoatRideFee()).append(" **\n");

        if (ticket.isLifeJacketRequired()) {
            sb.append("Life Jacket Fee: Rs ").append(ticket.getLifeJacketFee()).append("\n");
        }

        if (ticket.isParkingRequired()) {
            sb.append("Parking Fee: ** Rs ").append(ticket.getParkingFee()).append(" **\n");
        }

        sb.append("\n");
        sb.append("*** AMOUNT TO DRIVER ***").append("\n");
        sb.append("*** Rs ").append(ticket.getAmountToDriver()).append(" ***\n");
        sb.append("(Paid directly to driver)").append("\n");
        sb.append("\n");

        // Counter Payable
        if (ticket.getCounterPayable() > 0) {
            sb.append("===== COUNTER PAYMENT =====").append("\n");
            sb.append("*** PAYABLE AT COUNTER ***\n");
            sb.append("*** Rs ").append(ticket.getCounterPayable()).append(" ***\n");
            sb.append("(PARKING FEE ONLY)").append("\n");
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
     * Center text within the line width
     */
    private static String centerText(String text) {
        if (text.length() >= LINE_WIDTH) {
            return text.substring(0, LINE_WIDTH);
        }
        int padding = (LINE_WIDTH - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text;
    }

    /**
     * Truncate text to fit within specified width
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Format date time string for thermal printer (condensed)
     */
    private static String formatDateTime(String formattedTime) {
        // Input format: "dd-MM-yyyy  HH:mm:ss"
        // Output: "dd/MM HH:mm"
        if (formattedTime == null || formattedTime.length() < 19) return formattedTime;
        String date = formattedTime.substring(0, 10).replace("-", "/");
        String time = formattedTime.substring(12, 17); // HH:mm
        return date + " " + time;
    }
}

