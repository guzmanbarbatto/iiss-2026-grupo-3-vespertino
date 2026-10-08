package com.ioteste.engine.domain;

public class Habitacion {

    // TO-DO : Agregar anotaciones @Entity, @Id y @GeneratedValue cuando se conecte JPA.
    private Integer id;
    private String nombre;
    private Double temperaturaEsperada;
    private String idTermostato;
    private String idSwitch;
    
    // TO-DO : Agregado para conocer el consumo para no superar la carga máxima.
    private Double consumo; 

    public Habitacion(Integer id, String nombre, Double temperaturaEsperada, String idTermostato, String idSwitch, Double consumo) {
        this.id = id;
        this.nombre = nombre;
        this.temperaturaEsperada = temperaturaEsperada;
        this.idTermostato = idTermostato;
        this.idSwitch = idSwitch;
        this.consumo = consumo;
    }

    public Habitacion(String nombre, Double temperaturaEsperada, String idTermostato, String idSwitch, Double consumo) {
        this.nombre = nombre;
        this.temperaturaEsperada = temperaturaEsperada;
        this.idTermostato = idTermostato;
        this.idSwitch = idSwitch;
        this.consumo = consumo;
    }

    public Habitacion() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Double getTemperaturaEsperada() { return temperaturaEsperada; }
    public void setTemperaturaEsperada(Double temperaturaEsperada) { this.temperaturaEsperada = temperaturaEsperada; }

    public String getIdTermostato() { return idTermostato; }
    public void setIdTermostato(String idTermostato) { this.idTermostato = idTermostato; }

    public String getIdSwitch() { return idSwitch; }
    public void setIdSwitch(String idSwitch) { this.idSwitch = idSwitch; }

    public Double getConsumo() { return consumo; }
    public void setConsumo(Double consumo) { this.consumo = consumo; }
}