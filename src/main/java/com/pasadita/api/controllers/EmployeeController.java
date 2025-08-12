package com.pasadita.api.controllers;

import com.pasadita.api.dto.employee.*;
import com.pasadita.api.services.employee.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = {"http://localhost:8000", "http://localhost:63343", "http://localhost:63342"})
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<EmployeeResponseDto>> getAllEmployees() {
        List<EmployeeResponseDto> employees = employeeService.findAll();
        return ResponseEntity.ok(employees);
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponseDto> getEmployeeById(@PathVariable Long id) {
        return employeeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PostMapping("/save")
    public ResponseEntity<?> saveEmployee(@Valid @RequestBody EmployeeCreateDto employeeDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(getValidationErrors(result));
        }

        try {
            EmployeeResponseDto savedEmployee = employeeService.save(employeeDto)
                    .orElseThrow(() -> new RuntimeException("Error al guardar el empleado"));

            return ResponseEntity.status(HttpStatus.CREATED).body(savedEmployee);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error al guardar el empleado: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateDto employeeDto, BindingResult result) {
        if (!employeeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        if (employeeDto.getUsername() != null) {
            employeeService.findByUsername(employeeDto.getUsername())
                    .ifPresent(existingEmployee -> {
                        if (!existingEmployee.getId().equals(id)) {
                            result.rejectValue("username", "exists", "El usuario ya existe");
                        }
                    });
        }

        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(getValidationErrors(result));
        }

        return employeeService.update(id, employeeDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteEmployee(@PathVariable Long id) {
        if (!employeeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        try {
            employeeService.deleteById(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Empleado eliminado correctamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error al eliminar el empleado: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<EmployeeResponseDto> searchEmployee(@RequestParam String username) {
        return employeeService.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-password/{id}")
    public ResponseEntity<?> changePassword(@PathVariable Long id, @Valid @RequestBody EmployeeChangePasswordDto passwordDto, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(getValidationErrors(result));
        }

        if (!employeeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        return employeeService.changePassword(id, passwordDto)
                .map(updatedEmployee -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Contraseña actualizada correctamente");
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @RequestBody EmployeeChangeStatusDto statusDto) {
        if (!employeeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        return employeeService.changeStatus(id, statusDto)
                .map(updatedEmployee -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Estado actualizado correctamente");
                    response.put("active", updatedEmployee.isActive());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, String> getValidationErrors(BindingResult result) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : result.getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }
}