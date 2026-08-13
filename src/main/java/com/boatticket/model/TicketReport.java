package com.boatticket.model;

/**
 * A single flattened row of ticket data used when building export reports
 * (Excel / PDF). Kept separate from {@link Ticket} because reports only
 * need a handful of display fields, not the full booking/fee logic.
 */
public class TicketReport {

    private final String ticketId;
    private final String bookingTime;
    private final String customerName;
    private final String contactNumber;
    private final int numberOfPeople;
    private final int rideDurationInHour;
    private final int totalFee;
    private final String driverName;
    private final int parkingFee;

    public TicketReport(
            String ticketId,
            String bookingTime,
            String customerName,
            String contactNumber,
            int numberOfPeople,
            int rideDurationInHour,
            int totalFee,
            String driverName,
            int parkingFee
    ) {
        this.ticketId = ticketId;
        this.bookingTime = bookingTime;
        this.customerName = customerName;
        this.contactNumber = contactNumber;
        this.numberOfPeople = numberOfPeople;
        this.rideDurationInHour = rideDurationInHour;
        this.totalFee = totalFee;
        this.driverName = driverName;
        this.parkingFee = parkingFee;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getBookingTime() {
        return bookingTime;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public int getNumberOfPeople() {
        return numberOfPeople;
    }

    public int getRideDurationInHour() {
        return rideDurationInHour;
    }

    public int getTotalFee() {
        return totalFee;
    }

    public String getDriverName() {
        return driverName;
    }

    public int getParkingFee() {
        return parkingFee;
    }
}