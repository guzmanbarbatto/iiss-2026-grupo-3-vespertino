package com.ioteste.api.Domain;

public class Habitacion{

    private Integer id;
    private String nombre;
    private Double temperaturaEsperada;
    private String idTermostato;
    private String idSwitch;

    public Habitacion(Integer id, String nombre, Double temperaturaEsperada, String idTermostato, String idSwitch) {
        this.id = id;
        this.nombre = nombre;
        this.temperaturaEsperada = temperaturaEsperada;
        this.idTermostato = idTermostato;
        this.idSwitch = idSwitch;
    }
    public Habitacion(String nombre, Double temperaturaEsperada, String idTermostato, String idSwitch){
        this.nombre = nombre;
        this.temperaturaEsperada = temperaturaEsperada;
        this.idTermostato = idTermostato;
        this.idSwitch = idSwitch;
    }
    public Habitacion() {}

    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Double getTemperaturaEsperada() {
        return temperaturaEsperada;
    }
    public void setTemperaturaEsperada(Double temperaturaEsperada) {
        this.temperaturaEsperada = temperaturaEsperada;
    }

    public String getIdTermostato() {
        return idTermostato;
    }
    public void setIdTermostato(String idTermostato) {
        this.idTermostato = idTermostato;
    }

    public String getIdSwitch() {
        return idSwitch;
    }
    public void setIdSwitch(String idSwitch) {
        this.idSwitch = idSwitch;
    }
}