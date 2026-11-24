package com.fraudshield.model;

public class Customer {
    private final String id;
    private final String email;
    private final String hashedPhone;
    private final double homeLatitude;
    private final double homeLongitude;

    public Customer(String id, String email, String hashedPhone, double homeLatitude, double homeLongitude) {
        this.id = id;
        this.email = email;
        this.hashedPhone = hashedPhone;
        this.homeLatitude = homeLatitude;
        this.homeLongitude = homeLongitude;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getHashedPhone() { return hashedPhone; }
    public double getHomeLatitude() { return homeLatitude; }
    public double getHomeLongitude() { return homeLongitude; }
}
