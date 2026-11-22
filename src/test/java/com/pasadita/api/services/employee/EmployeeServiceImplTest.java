package com.pasadita.api.services.employee;

import com.pasadita.api.dto.employee.EmployeeChangePasswordDto;
import com.pasadita.api.dto.employee.EmployeeChangeStatusDto;
import com.pasadita.api.dto.employee.EmployeeMapper;
import com.pasadita.api.dto.employee.EmployeeUpdateDto;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.enums.user.Position;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.services.auth.AuthSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthSessionService authSessionService;

    private Employee employee;
    private EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        employee = Employee.builder().id(7L).fullName("Juan Perez").username("cajero1")
                .position(Position.ROLE_CAJERO).phone("3312345678").active(true).password("old-hash").build();
        lenient().when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        lenient().when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new EmployeeServiceImpl(employeeRepository, passwordEncoder, new EmployeeMapper(), authSessionService);
    }

    @Test
    void deactivatingAnEmployeeRevokesEverySession() {
        service.changeStatus(7L, new EmployeeChangeStatusDto(false));

        assertThat(employee.isActive()).isFalse();
        verify(authSessionService).revokeAllSessions(7L);
    }

    @Test
    void activatingAnEmployeeKeepsItsSessions() {
        employee.setActive(false);

        service.changeStatus(7L, new EmployeeChangeStatusDto(true));

        verify(authSessionService, never()).revokeAllSessions(any());
    }

    @Test
    void updateThatDeactivatesTheEmployeeRevokesEverySession() {
        service.update(7L, updateDto(false));

        verify(authSessionService).revokeAllSessions(7L);
    }

    @Test
    void updateThatKeepsTheEmployeeActiveKeepsItsSessions() {
        service.update(7L, updateDto(true));

        verify(authSessionService, never()).revokeAllSessions(any());
    }

    @Test
    void passwordChangeRevokesEverySession() {
        when(passwordEncoder.encode("NuevaClave2026")).thenReturn("new-hash");

        service.changePassword(7L, new EmployeeChangePasswordDto("NuevaClave2026"));

        assertThat(employee.getPassword()).isEqualTo("new-hash");
        verify(authSessionService).revokeAllSessions(7L);
    }

    @Test
    void emptyPasswordChangesNothingAndKeepsTheSessions() {
        assertThat(service.changePassword(7L, new EmployeeChangePasswordDto(""))).isEmpty();

        verifyNoInteractions(authSessionService);
    }

    private static EmployeeUpdateDto updateDto(boolean active) {
        return EmployeeUpdateDto.builder().fullName("Juan Perez").username("cajero1")
                .position(Position.ROLE_CAJERO).phone("3312345678").active(active).build();
    }
}
