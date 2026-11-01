package com.EduardoMango.Biblioteca.feature.author;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "authors", uniqueConstraints = @UniqueConstraint(name = "uk_author_identity", columnNames = "identity_key"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column
    private String nacionalidad;

    @Column
    private LocalDate fechaNacimiento;

    @Column(name = "identity_key", length = 512)
    private String identityKey;

    @PreUpdate
    public void updateIdentity() {
        nombre = AuthorIdentity.cleanName(nombre);
        apellido = AuthorIdentity.cleanName(apellido);
        identityKey = AuthorIdentity.key(nombre, apellido, fechaNacimiento);
    }

    @PrePersist
    public void prePersist() {
        updateIdentity();
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Author author = (Author) o;
        return publicId != null && Objects.equals(publicId, author.publicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicId);
    }
}

