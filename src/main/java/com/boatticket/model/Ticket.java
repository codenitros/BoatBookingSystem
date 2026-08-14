package com.boatticket.model;

import com.boatticket.config.ConfigManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class Ticket {

  private final String ticketId;
  private final LocalDateTime bookingTime;

  private String customerName;
  private String contactNumber;
  private int numberOfPeople;
  private boolean lifeJacketRequired;
  private int lifeJacketCount;
  private boolean parkingRequired;
  private VehicleType vehicleType;
  private String vehicleNumber;
  private BoatOwner boatOwner;
  private String bookedBy;
  private int rideDurationInHour;
  private String boatHRNumber;
  private String boatHONumber;
  private String jetty;

  private Integer storedBoatRideFee;
  private Integer storedLifeJacketFee;
  private Integer storedParkingFee;
  private Integer storedTotalFee;

  public Ticket() {
    this("BT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), LocalDateTime.now());
  }

  public Ticket(String ticketId, LocalDateTime bookingTime) {
    this.ticketId = ticketId;
    this.bookingTime = bookingTime;
  }

  public int getBoatRideFee() {
    if (storedBoatRideFee != null) {
      return storedBoatRideFee;
    }

    if (rideDurationInHour == 2) {
      return ConfigManager.getBoatRideFee2Hours();
    }

    return ConfigManager.getBoatRideFeePerHour() * rideDurationInHour;
  }

  public int getLifeJacketFee() {
    if (storedLifeJacketFee != null) {
      return storedLifeJacketFee;
    }

    return lifeJacketRequired ? ConfigManager.getLifeJacketFee() * lifeJacketCount : 0;
  }

  public int getParkingFee() {
    if (storedParkingFee != null) {
      return storedParkingFee;
    }

    if (!parkingRequired || vehicleType == null) {
      return 0;
    }

    return switch (vehicleType) {
      case CAR -> ConfigManager.getParkingFeeCar();
      case BUS -> ConfigManager.getParkingFeeBus();
      case TEMPOTRAVELLER -> ConfigManager.getParkingFeeTempoTraveller();
    };
  }

  public int getAmountToDriver() {
    return getBoatRideFee() + getLifeJacketFee();
  }

  public int getCounterPayable() {
    return getParkingFee();
  }

  public int getTotalFee() {
    if (storedTotalFee != null) {
      return storedTotalFee;
    }

    return getBoatRideFee() + getLifeJacketFee() + getParkingFee();
  }

  public void setStoredFees(int boatRideFee, int lifeJacketFee, int parkingFee, int totalFee) {
    this.storedBoatRideFee = boatRideFee;
    this.storedLifeJacketFee = lifeJacketFee;
    this.storedParkingFee = parkingFee;
    this.storedTotalFee = totalFee;
  }

  public String getFormattedBookingTime() {
    return bookingTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy  HH:mm:ss"));
  }

  public String getVehicleTypeDisplay() {
    if (vehicleType == null) {
      return "-";
    }

    return switch (vehicleType) {
      case CAR -> "Car";
      case BUS -> "Bus";
      case TEMPOTRAVELLER -> "Tempo Traveller";
    };
  }

  public String getTicketId() {
    return ticketId;
  }

  public LocalDateTime getBookingTime() {
    return bookingTime;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public String getContactNumber() {
    return contactNumber;
  }

  public void setContactNumber(String contactNumber) {
    this.contactNumber = contactNumber;
  }

  public int getNumberOfPeople() {
    return numberOfPeople;
  }

  public void setNumberOfPeople(int numberOfPeople) {
    this.numberOfPeople = numberOfPeople;
  }

  public boolean isLifeJacketRequired() {
    return lifeJacketRequired;
  }

  public void setLifeJacketRequired(boolean lifeJacketRequired) {
    this.lifeJacketRequired = lifeJacketRequired;
  }

  public int getLifeJacketCount() {
    return lifeJacketCount;
  }

  public void setLifeJacketCount(int lifeJacketCount) {
    this.lifeJacketCount = lifeJacketCount;
  }

  public boolean isParkingRequired() {
    return parkingRequired;
  }

  public void setParkingRequired(boolean parkingRequired) {
    this.parkingRequired = parkingRequired;
  }

  public VehicleType getVehicleType() {
    return vehicleType;
  }

  public void setVehicleType(VehicleType vehicleType) {
    this.vehicleType = vehicleType;
  }

  public String getVehicleNumber() {
    return vehicleNumber;
  }

  public void setVehicleNumber(String vehicleNumber) {
    this.vehicleNumber = vehicleNumber;
  }

  public BoatOwner getBoatOwner() {
    return boatOwner;
  }

  public void setBoatOwner(BoatOwner boatOwner) {
    this.boatOwner = boatOwner;
  }

  public String getBookedBy() {
    return bookedBy;
  }

  public void setBookedBy(String bookedBy) {
    this.bookedBy = bookedBy;
  }

  public int getRideDurationInHour() {
    return rideDurationInHour;
  }

  public void setRideDurationInHour(int rideDurationInHour) {
    this.rideDurationInHour = rideDurationInHour;
  }

  public String getBoatHRNumber() {
    return boatHRNumber;
  }

  public void setBoatHRNumber(String boatHRNumber) {
    this.boatHRNumber = boatHRNumber;
  }

  public String getBoatHONumber() {
    return boatHONumber;
  }

  public void setBoatHONumber(String boatHONumber) {
    this.boatHONumber = boatHONumber;
  }

  public String getJetty() {
    return jetty;
  }

  public void setJetty(String jetty) {
    this.jetty = jetty;
  }

  public enum VehicleType {
    CAR,
    BUS,
    TEMPOTRAVELLER
  }
}
