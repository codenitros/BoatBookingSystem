package com.boatticket.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class Ticket {

    public static final int BOAT_RIDE_FEE_PER_HOUR = 1600;
    public static final int LIFE_JACKET_FEE           = 10;
    public static final int PARKING_FEE_CAR            = 20;
    public static final int PARKING_FEE_BUS             = 40;
    public static final int PARKING_FEE_TEMPOTRAVELLER   = 35;

    private final String        ticketId;
    private final LocalDateTime bookingTime;
    private String      customerName;
    private String      contactNumber;
    private int         numberOfPeople;
    private boolean     lifeJacketRequired;
    private int         lifeJacketCount;
    private boolean     parkingRequired;
    private VehicleType vehicleType;
    private String      vehicleNumber;
    private BoatOwner   boatOwner;
    private String      bookedBy;

    public Ticket() {
        this.ticketId    = "BT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.bookingTime = LocalDateTime.now();
    }

    public int getBoatRideFee() { return BOAT_RIDE_FEE_PER_HOUR ; }

    public int getLifeJacketFee() {
        return lifeJacketRequired ? LIFE_JACKET_FEE * lifeJacketCount : 0;
    }

    public int getParkingFee() {
        if (!parkingRequired || vehicleType == null) return 0;
        return switch (vehicleType) {
            case CAR         -> PARKING_FEE_CAR;
            case BUS         -> PARKING_FEE_BUS;
            case TEMPOTRAVELLER  -> PARKING_FEE_TEMPOTRAVELLER;
        };
    }

    public int getAmountToDriver()   { return getBoatRideFee() + getLifeJacketFee(); }
    public int getCounterPayable()   { return getParkingFee(); }
    public int getTotalFee()         { return getBoatRideFee() + getLifeJacketFee() + getParkingFee(); }

    public String getFormattedBookingTime() {
        return bookingTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy  HH:mm:ss"));
    }

    public String getVehicleTypeDisplay() {
        if (vehicleType == null) return "-";
        return switch (vehicleType) {
            case CAR -> "Car";
            case BUS -> "Bus";
            case TEMPOTRAVELLER -> "Tempo Traveller";
        };
    }

    public String      getTicketId()             { return ticketId; }
    public LocalDateTime getBookingTime()         { return bookingTime; }
    public String      getCustomerName()          { return customerName; }
    public void        setCustomerName(String v)  { customerName = v; }
    public String      getContactNumber()         { return contactNumber; }
    public void        setContactNumber(String v) { contactNumber = v; }
    public int         getNumberOfPeople()        { return numberOfPeople; }
    public void        setNumberOfPeople(int v)   { numberOfPeople = v; }
    public boolean     isLifeJacketRequired()     { return lifeJacketRequired; }
    public void        setLifeJacketRequired(boolean v) { lifeJacketRequired = v; }
    public int         getLifeJacketCount()       { return lifeJacketCount; }
    public void        setLifeJacketCount(int v)  { lifeJacketCount = v; }
    public boolean     isParkingRequired()        { return parkingRequired; }
    public void        setParkingRequired(boolean v){ parkingRequired = v; }
    public VehicleType getVehicleType()           { return vehicleType; }
    public void        setVehicleType(VehicleType v){ vehicleType = v; }
    public String      getVehicleNumber()         { return vehicleNumber; }
    public void        setVehicleNumber(String v) { vehicleNumber = v; }
    public BoatOwner   getBoatOwner()             { return boatOwner; }
    public void        setBoatOwner(BoatOwner v)  { boatOwner = v; }
    public String      getBookedBy()              { return bookedBy; }
    public void        setBookedBy(String v)      { bookedBy = v; }

    public enum VehicleType { CAR, BUS, TEMPOTRAVELLER }
}
