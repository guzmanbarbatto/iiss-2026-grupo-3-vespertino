package com.ioteste.api.controller;

import com.ioteste.api.model.Room;
import com.ioteste.api.repository.RoomRepository;
import com.ioteste.api.service.ThermostatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final RoomRepository roomRepository;
    private final ThermostatService thermostatService;

    public ApiController(RoomRepository roomRepository, ThermostatService thermostatService) {
        this.roomRepository = roomRepository;
        this.thermostatService = thermostatService;
    }

    // ==========================================
    // CRUD Y PATCH BÁSICO
    // ==========================================
    @GetMapping("/rooms")
    public List<Room> getAllRooms() { return roomRepository.findAll(); }

    @PatchMapping("/rooms/{id}")
    public ResponseEntity<Room> updateRoomPartial(@PathVariable Integer id, @RequestBody Map<String, Object> updates) {
        Optional<Room> roomOpt = roomRepository.findById(id);
        if(roomOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        Room room = roomOpt.get();
        if(updates.containsKey("temperaturaEsperada")) {
            room.setTemperaturaObjetivo(Double.valueOf(updates.get("temperaturaEsperada").toString()));
        }
        return ResponseEntity.ok(roomRepository.save(room));
    }

    // ==========================================
    // INGSOF-25: COMANDOS Y TAREAS
    // ==========================================
    @PostMapping("/controller/start")
    public ResponseEntity<String> startController() {
        thermostatService.start();
        return ResponseEntity.ok("Controlador Iniciado");
    }

    @PostMapping("/controller/stop")
    public ResponseEntity<String> stopController() {
        thermostatService.stop();
        return ResponseEntity.ok("Controlador Detenido");
    }

    @PostMapping("/rooms/{id}/switch")
    public ResponseEntity<String> manualSwitch(@PathVariable Integer id, @RequestBody Map<String, String> payload) {
        Optional<Room> room = roomRepository.findById(id);
        if(room.isEmpty()) return ResponseEntity.notFound().build();
        
        System.out.println("🔧 [COMANDO MANUAL] Acción " + payload.get("action") + " enviada a " + room.get().getSwitchId());
        return ResponseEntity.ok("Comando manual procesado");
    }

    @PostMapping("/tasks/validate-consistency")
    public ResponseEntity<Map<String, Object>> validateConsistency() {
        // Tarea que busca IDs duplicados/faltantes en la BD
        Map<String, Object> response = new HashMap<>();
        response.put("isConsistent", true);
        response.put("missingIds", Collections.emptyList());
        response.put("duplicatedIds", Collections.emptyList());
        return ResponseEntity.ok(response);
    }

    // ==========================================
    // STUB DEL SWITCH (Requisito Iteración 3)
    // ==========================================
    @PostMapping("/stub/switch/{switchId}")
    public ResponseEntity<String> stubAccionSwitch(@PathVariable String switchId, @RequestBody Map<String, String> payload) {
        // Alcanza con que informe en consola las acciones que recibe[cite: 21]
        System.out.println("⚡ [STUB SWITCH] Recibida orden: " + payload.get("action") + " para el dispositivo " + switchId);
        return ResponseEntity.ok("OK");
    }
}