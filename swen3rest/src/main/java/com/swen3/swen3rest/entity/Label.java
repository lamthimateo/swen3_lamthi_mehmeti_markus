package com.swen3.swen3rest.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "labels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // entity constraints mirror the column (VARCHAR(255)); the stricter API limit of 50 lives in CreateLabelRequest
    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, unique = true)
    private String name;
}
