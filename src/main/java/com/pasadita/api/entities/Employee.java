package com.pasadita.api.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pasadita.api.enums.user.Position;
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
    private String fullName;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @ExistsEmployee
    private String username;

    @Enumerated(EnumType.STRING)
    private Position position;

    private String phone;
    private boolean active;

}
