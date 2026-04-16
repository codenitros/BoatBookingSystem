package com.boatticket.model;

public class BoatOwner {
    private final int    id;
    private final String driverName;
    private final String boatHRNumber;
    private final String boatHONumber;
    private final String jetty;
    private final int    capacity;
    private final String contact;

    public BoatOwner(int id, String driverName, String boatHRNumber,
                     String boatHONumber, String jetty, int capacity, String contact) {
        this.id = id;
        this.driverName = driverName;
        this.boatHRNumber   = boatHRNumber;
        this.boatHONumber = boatHONumber;
        this.jetty = jetty;
        this.capacity   = capacity;
        this.contact    = contact;
    }

    /** Shown in the ComboBox dropdown */
    @Override
    public String toString() {
        return id + " | " + driverName + " | " + jetty + " | " + capacity;
    }

    public int    getId()         { return id; }
    public String getDriverName() { return driverName; }
    public int    getCapacity()   { return capacity; }
    public String getContact()    { return contact; }
    public String getBoatHRNumber() { return boatHRNumber;}
    public String getBoatHONumber() { return boatHONumber;}
    public String getJetty() { return jetty;}
}
