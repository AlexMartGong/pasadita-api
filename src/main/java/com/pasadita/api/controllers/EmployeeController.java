package com.pasadita.api.controllers;

import com.pasadita.api.dto.employee.*;
import com.pasadita.api.services.employee.EmployeeService;
import com.pasadita.api.utils.ValidationUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_CAJERO', 'ROLE_PEDIDOS')")
    @GetMapping("/all")
    public ResponseEntity<List<EmployeeResponseDto>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.findAll());
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
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.save(employeeDto));
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
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
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

        employeeService.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Empleado eliminado correctamente"));
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
            return ResponseEntity.badRequest().body(ValidationUtils.getValidationErrors(result));
        }

        if (!employeeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        return employeeService.changePassword(id, passwordDto)
                .map(updatedEmployee -> ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente")))
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @PutMapping("/change-status/{id}")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @RequestBody EmployeeChangeStatusDto statusDto) {
        if (!employeeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        return employeeService.changeStatus(id, statusDto)
                .map(updatedEmployee -> ResponseEntity.ok(Map.of(
                        "message", "Estado actualizado correctamente",
                        "active", updatedEmployee.isActive())))
                .orElse(ResponseEntity.notFound().build());
    }
}
