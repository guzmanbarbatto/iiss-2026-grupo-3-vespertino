package com.ioteste.subscriber.Domain;

public class EventoTemperatura {
    private int id;
    private double tC;
    private double tF;
    private double ts;

    public EventoTemperatura() {

    }
    public EventoTemperatura(int id, double tC, double tF, double ts) {
        this.id = id;
        this.tC = tC;
        this.tF = tF;
        this.ts = ts;
    }

    public int getid() {
        return id;
    }
    public double gettC() {
        return tC;
    }
    public double gettF() {
        return tF;
    }
    public double getts() {
        return ts;
    }
}
