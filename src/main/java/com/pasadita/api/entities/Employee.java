package com.pasadita.api.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pasadita.api.enums.Position;
import com.pasadita.api.validation.ExistsEmployee;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long id;

    @Column(name = "full_name")
    @NotBlank
    private String fullName;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank
    private String password;

    @ExistsEmployee
    @NotBlank
    private String username;

    @Enumerated(EnumType.STRING)
    @NotNull
    private Position position;

    @NotBlank
    private String phone;

    private boolean active;

}
