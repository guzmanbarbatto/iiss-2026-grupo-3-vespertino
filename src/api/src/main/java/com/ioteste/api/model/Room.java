package com.ioteste.api.model;
import jakarta.persistence.*;

@Entity
@Table(name = "habitacion")
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "nombre")
    private String nombre;
    
    @Column(name = "termostato_id")
    private String termostatoId;
    
    @Column(name = "switch_id")
    private String switchId;
    
    @Column(name = "temperatura_objetivo")
    private Double temperaturaObjetivo;

    // Getters y Setters
    public Integer getId() { return id; }
    public String getSwitchId() { return switchId; }
    public Double getTemperaturaObjetivo() { return temperaturaObjetivo; }
    public void setTemperaturaObjetivo(Double temperaturaObjetivo) { this.temperaturaObjetivo = temperaturaObjetivo; }
}