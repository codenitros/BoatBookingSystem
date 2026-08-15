package com.boatticket.util;

import com.boatticket.config.ConfigManager;
import com.boatticket.model.Ticket;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ThermalPrinterManager {

    /*
     * Standard 80 mm thermal receipt printers normally support
     * approximately 48 characters per line using their normal
     * fixed-width font.
     *
     * If the actual deployed printer is a 58 mm printer, this can
     * later be changed to 32.
     */
    private static final int LINE_WIDTH = 48;

    private static final String SEPARATOR = "-".repeat(LINE_WIDTH);

    /*
     * Layout:
     *
     * 18 characters for the label
     * 1 space
     * remaining characters for the value
     */
    private static final int LABEL_WIDTH = 18;
    private static final int VALUE_WIDTH =
            LINE_WIDTH - LABEL_WIDTH;

    /*
     * Printer names that represent virtual/file printers rather
     * than the actual thermal printer.
     */
    private static final String[] VIRTUAL_PRINTER_KEYWORDS = {
            "microsoft print to pdf",
            "microsoft xps document writer",
            "onenote",
            "send to onenote",
            "fax"
    };

    /**
     * Resolves the printer configured in Config.properties.
     *
     * If printer.name is configured, that exact printer is used.
     * Otherwise, the Windows default printer is used.
     */
    public static PrintService resolvePrinter() throws PrintException {

        PrintService[] allPrinters =
                PrintServiceLookup.lookupPrintServices(null, null);

        String configuredName =
                ConfigManager.getPrinterName();

        /*
         * If a printer name has been configured, always use
         * that printer.
         */
        if (!configuredName.isEmpty()) {

            for (PrintService printer : allPrinters) {

                if (printer.getName()
                        .equalsIgnoreCase(configuredName)) {

                    return printer;
                }
            }

            throw new PrintException(
                    "Configured printer \"" +
                            configuredName +
                            "\" was not found.\n\n" +
                            "Available printers: " +
                            listPrinterNames(allPrinters)
            );
        }

        /*
         * Otherwise use the Windows default printer.
         */
        PrintService defaultPrinter =
                PrintServiceLookup.lookupDefaultPrintService();

        if (defaultPrinter == null) {
            throw new PrintException(
                    "No default printer found on the system."
            );
        }

        /*
         * Do not accidentally send the ticket to a virtual
         * PDF/XPS printer.
         */
        if (isVirtualPrinter(defaultPrinter.getName())) {

            throw new PrintException(
                    "The Windows default printer is \"" +
                            defaultPrinter.getName() +
                            "\", which is a virtual/file printer.\n\n" +
                            "Set printer.name in Config.properties " +
                            "to the actual thermal printer.\n\n" +
                            "Available printers: " +
                            listPrinterNames(allPrinters)
            );
        }

        return defaultPrinter;
    }

    /**
     * Kept for compatibility with any existing code.
     */
    @Deprecated
    public static PrintService getDefaultPrinter()
            throws PrintException {

        return resolvePrinter();
    }

    /**
     * Checks whether a printer is a known virtual printer.
     */
    private static boolean isVirtualPrinter(
            String printerName) {

        if (printerName == null) {
            return false;
        }

        String lower =
                printerName.toLowerCase(Locale.ROOT);

        for (String keyword : VIRTUAL_PRINTER_KEYWORDS) {

            if (lower.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a readable list of installed printers.
     */
    private static String listPrinterNames(
            PrintService[] printers) {

        if (printers == null || printers.length == 0) {
            return "(none found)";
        }

        return Stream.of(printers)
                .map(PrintService::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Prints the ticket to the configured thermal printer.
     */
    public static String printTicket(Ticket ticket)
            throws PrintException, IOException {

        PrintService selectedPrinter =
                resolvePrinter();

        String content =
                formatTicketContent(ticket);

        /*
         * Send the ticket as fixed-width plain text.
         *
         * The layout itself is controlled by this class.
         */
        byte[] bytes =
                content.getBytes(StandardCharsets.UTF_8);

        DocFlavor flavor = null;

        DocFlavor[] tryFlavors = {
                DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8,
                DocFlavor.BYTE_ARRAY.AUTOSENSE,
                DocFlavor.INPUT_STREAM.AUTOSENSE
        };

        for (DocFlavor tryFlavor : tryFlavors) {

            if (selectedPrinter.isDocFlavorSupported(
                    tryFlavor)) {

                flavor = tryFlavor;
                break;
            }
        }

        if (flavor == null) {
            flavor =
                    DocFlavor.BYTE_ARRAY.AUTOSENSE;
        }

        Doc doc =
                new SimpleDoc(
                        bytes,
                        flavor,
                        null
                );

        DocPrintJob job =
                selectedPrinter.createPrintJob();

        PrintRequestAttributeSet attributes =
                new HashPrintRequestAttributeSet();

        attributes.add(new Copies(1));

        job.print(doc, attributes);

        return selectedPrinter.getName();
    }

    /**
     * Prints the exact same formatted ticket to the IntelliJ
     * console for development/testing.
     */
    public static void printTicketToConsole(
            Ticket ticket) {

        String content =
                formatTicketContent(ticket);

        System.out.println();
        System.out.println(
                "=".repeat(LINE_WIDTH)
        );
        System.out.println(
                "THERMAL PRINTER PREVIEW"
        );
        System.out.println(
                "=".repeat(LINE_WIDTH)
        );
        System.out.println(content);
        System.out.println(
                "=".repeat(LINE_WIDTH)
        );
    }

    /**
     * Builds the complete thermal ticket.
     *
     * Every generated line is kept within LINE_WIDTH so that
     * the printer does not unexpectedly wrap text.
     */
    private static String formatTicketContent(
            Ticket ticket) {

        StringBuilder sb =
                new StringBuilder();

        // ============================================================
        // HEADER
        // ============================================================

        sb.append(
                centerText(
                        "SHARAVATI BACKWATER BOATING"
                )
        ).append('\n');

        sb.append(
                centerText(
                        "HONNAVAR, RECEIPT"
                )
        ).append('\n');

        sb.append(SEPARATOR).append('\n');

        // ============================================================
        // BASIC TICKET INFORMATION
        // ============================================================

        appendKeyValue(
                sb,
                "Ticket ID",
                ticket.getTicketId()
        );

        appendKeyValue(
                sb,
                "Date & Time",
                formatDateTime(
                        ticket.getFormattedBookingTime()
                )
        );

        appendKeyValue(
                sb,
                "Persons",
                String.valueOf(
                        ticket.getNumberOfPeople()
                )
        );

        sb.append('\n');

        // ============================================================
        // DRIVER AND BOAT DETAILS
        // ============================================================

        if (ticket.getBoatOwner() != null) {

            appendSectionHeader(
                    sb,
                    "DRIVER & BOAT DETAILS"
            );

            appendKeyValue(
                    sb,
                    "Driver",
                    ticket.getBoatOwner()
                            .getDriverName()
            );

            appendKeyValue(
                    sb,
                    "Driver Number",
                    ticket.getBoatOwner()
                            .getContact()
            );

            appendKeyValue(
                    sb,
                    "Boat HR",
                    ticket.getBoatOwner()
                            .getBoatHRNumber()
            );

            appendKeyValue(
                    sb,
                    "Capacity",
                    ticket.getBoatOwner()
                            .getCapacity()
                            + " persons"
            );

            sb.append('\n');
        }

        // ============================================================
        // RIDE DETAILS
        // ============================================================

        appendSectionHeader(
                sb,
                "RIDE DETAILS"
        );

        appendKeyValue(
                sb,
                "Duration",
                ticket.getRideDurationInHour()
                        + " HOUR(S)"
        );

        if (ticket.isLifeJacketRequired()) {

            appendKeyValue(
                    sb,
                    "Life Jackets",
                    String.valueOf(
                            ticket.getLifeJacketCount()
                    )
            );
        }

        if (ticket.isParkingRequired()) {

            String parkingDetails =
                    ticket.getVehicleTypeDisplay();

            if (ticket.getVehicleNumber() != null
                    && !ticket.getVehicleNumber()
                    .isBlank()) {

                parkingDetails +=
                        " | " +
                                ticket.getVehicleNumber()
                                        .toUpperCase(
                                                Locale.ROOT
                                        );
            }

            appendKeyValue(
                    sb,
                    "Vehicle Parking",
                    parkingDetails
            );
        }

        sb.append('\n');

        // ============================================================
        // FEE BREAKDOWN
        // ============================================================

        appendSectionHeader(
                sb,
                "FEE BREAKDOWN"
        );

        appendAmountRow(
                sb,
                "Boat Ride Fee",
                ticket.getBoatRideFee()
        );

        if (ticket.isLifeJacketRequired()) {

            appendAmountRow(
                    sb,
                    "Life Jacket Fee",
                    ticket.getLifeJacketFee()
            );
        }

        if (ticket.isParkingRequired()) {

            appendAmountRow(
                    sb,
                    "Parking Fee",
                    ticket.getParkingFee()
            );
        }

        sb.append('\n');

        // ============================================================
        // COUNTER PAYMENT
        // ============================================================

        if (ticket.getCounterPayable() > 0) {

            sb.append(SEPARATOR)
                    .append('\n');

            sb.append(
                    centerText("COUNTER PAYMENT")
            ).append('\n');

            sb.append(
                    centerText(
                            "PAYABLE AT COUNTER"
                    )
            ).append('\n');

            sb.append(
                    centerText(
                            "Rs " +
                                    ticket.getCounterPayable()
                    )
            ).append('\n');

            sb.append(
                    centerText(
                            "(PARKING FEE ONLY)"
                    )
            ).append('\n');

            sb.append(SEPARATOR)
                    .append('\n');

            sb.append('\n');
        }

        // ============================================================
        // FOOTER
        // ============================================================

        sb.append(SEPARATOR)
                .append('\n');

        sb.append(
                centerText("Thank You!")
        ).append('\n');

        sb.append(
                centerText(
                        "Safe & Happy Journey"
                )
        ).append('\n');

        /*
         * Feed the paper after the final line so that the printer
         * does not cut/stop immediately against the footer.
         */
        sb.append('\n');
        sb.append('\n');
        sb.append('\n');

        return sb.toString();
    }

    /**
     * Adds a section heading followed by a full-width separator.
     */
    private static void appendSectionHeader(
            StringBuilder sb,
            String title) {

        sb.append(
                sanitize(title)
        ).append('\n');

        sb.append(SEPARATOR)
                .append('\n');
    }

    /**
     * Creates a fixed two-column row.
     *
     * Example:
     *
     * Driver:           Ravi Kumar
     * Driver Number:    9988011006
     * Boat HR:          HR-001
     */
    private static void appendKeyValue(
            StringBuilder sb,
            String label,
            String value) {

        String safeLabel =
                sanitize(label);

        String safeValue =
                sanitize(value);

        /*
         * Make sure the label never occupies more than
         * LABEL_WIDTH characters.
         */
        if (safeLabel.length()
                > LABEL_WIDTH - 1) {

            safeLabel =
                    safeLabel.substring(
                            0,
                            LABEL_WIDTH - 1
                    );
        }

        String labelPart =
                String.format(
                        "%-" +
                                LABEL_WIDTH +
                                "s",
                        safeLabel + ":"
                );

        String[] lines =
                wrapText(
                        safeValue,
                        VALUE_WIDTH
                );

        if (lines.length == 0) {

            sb.append(labelPart)
                    .append('\n');

            return;
        }

        /*
         * First line:
         *
         * Label:           Value
         */
        sb.append(labelPart)
                .append(lines[0])
                .append('\n');

        /*
         * Continuation lines start at the same position
         * where the value column starts.
         */
        String continuation =
                " ".repeat(LABEL_WIDTH);

        for (int i = 1;
             i < lines.length;
             i++) {

            sb.append(continuation)
                    .append(lines[i])
                    .append('\n');
        }
    }

    /**
     * Creates a properly aligned money row.
     *
     * Example:
     *
     * Boat Ride Fee:                         Rs 1500
     * Life Jacket Fee:                         Rs 10
     * Parking Fee:                             Rs 50
     */
    private static void appendAmountRow(
            StringBuilder sb,
            String label,
            int amount) {

        String safeLabel =
                sanitize(label);

        /*
         * Keep the amount on the right side of the
         * receipt.
         */
        int amountWidth = 12;

        int labelWidth =
                LINE_WIDTH - amountWidth;

        if (safeLabel.length()
                > labelWidth - 1) {

            safeLabel =
                    safeLabel.substring(
                            0,
                            labelWidth - 1
                    );
        }

        String left =
                String.format(
                        "%-" +
                                labelWidth +
                                "s",
                        safeLabel
                );

        String right =
                String.format(
                        "%" +
                                amountWidth +
                                "s",
                        "Rs " + amount
                );

        sb.append(left)
                .append(right)
                .append('\n');
    }

    /**
     * Centers text inside the actual receipt width.
     */
    private static String centerText(
            String text) {

        String safeText =
                sanitize(text);

        /*
         * Prevent overflow.
         */
        if (safeText.length()
                >= LINE_WIDTH) {

            return safeText.substring(
                    0,
                    LINE_WIDTH
            );
        }

        int padding =
                (LINE_WIDTH -
                        safeText.length()) / 2;

        return " ".repeat(
                Math.max(0, padding)
        ) + safeText;
    }

    /**
     * Wraps a long value without breaking words unless
     * a single word itself is longer than the available width.
     */
    private static String[] wrapText(
            String text,
            int maxWidth) {

        if (text == null
                || text.isBlank()) {

            return new String[]{""};
        }

        String safeText =
                sanitize(text).trim();

        List<String> lines =
                new ArrayList<>();

        while (safeText.length()
                > maxWidth) {

            int breakPosition =
                    safeText.lastIndexOf(
                            ' ',
                            maxWidth
                    );

            /*
             * If there is no suitable space,
             * split at maxWidth.
             */
            if (breakPosition <= 0) {
                breakPosition = maxWidth;
            }

            lines.add(
                    safeText
                            .substring(
                                    0,
                                    breakPosition
                            )
                            .trim()
            );

            safeText =
                    safeText
                            .substring(
                                    breakPosition
                            )
                            .trim();
        }

        if (!safeText.isEmpty()) {
            lines.add(safeText);
        }

        return lines.toArray(
                new String[0]
        );
    }

    /**
     * Thermal printers generally have a limited character set.
     *
     * This also removes control characters that could accidentally
     * move the printer cursor or break the receipt.
     */
    private static String sanitize(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll(
                        "[^\\x20-\\x7E]",
                        "?"
                );
    }

    /**
     * Converts:
     *
     * dd-MM-yyyy  HH:mm:ss
     *
     * into:
     *
     * dd/MM/yyyy HH:mm
     */
    private static String formatDateTime(
            String formattedTime) {

        if (formattedTime == null) {
            return "";
        }

        try {

            if (formattedTime.length()
                    >= 19) {

                String date =
                        formattedTime
                                .substring(0, 10)
                                .replace("-", "/");

                String time =
                        formattedTime
                                .substring(12, 17);

                return date + " " + time;
            }

        } catch (Exception ignored) {
            // Return original value below.
        }

        return formattedTime;
    }
}