package com.ioteste.subscriber.Domain;

public class Habitacion {
        private int id;
        private String termostato;
        private String switchId;
        private double temperaturaObjetivo;

        public Habitacion(int id, String termostato, String switchId, double temperaturaObjetivo) {
            this.id = id;
            this.termostato = termostato;
            this.switchId = switchId;
            this.temperaturaObjetivo = temperaturaObjetivo;
        }

        public int getId() {
            return id;
        }
        public String getTermostato() {
            return termostato;
        }
        public String getSwitchId() {
            return switchId;
        }
        public double getTemperaturaObjetivo() {
            return temperaturaObjetivo;
        }
}
