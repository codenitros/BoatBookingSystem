package com.boatticket.model;

public class BoatOwner {
    private final int    id;
    private final String driverName;
    private final String boatName;
    private final String boatNumber;
    private final int    capacity;
    private final String contact;

    public BoatOwner(int id, String driverName, String boatName,
                     String boatNumber, int capacity, String contact) {
        this.id = id;
        this.driverName = driverName;
        this.boatName   = boatName;
        this.boatNumber = boatNumber;
        this.capacity   = capacity;
        this.contact    = contact;
    }

    /** Shown in the ComboBox dropdown */
    @Override
    public String toString() {
        return driverName + "  |  " + boatName + " (" + boatNumber + ")";
    }

    public int    getId()         { return id; }
    public String getDriverName() { return driverName; }
    public String getBoatName()   { return boatName; }
    public String getBoatNumber() { return boatNumber; }
    public int    getCapacity()   { return capacity; }
    public String getContact()    { return contact; }
}
