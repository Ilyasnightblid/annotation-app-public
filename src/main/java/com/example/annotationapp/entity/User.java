package com.example.annotationapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username; // login

    @Column(nullable = false)
    private String password;

    private String nom;
    private String prenom;

    // NOUVEAU CHAMP POUR LA SUPPRESSION LOGIQUE
    @Column(nullable = false) // On veut s'assurer qu'il a toujours une valeur
    private boolean enabled = true; // Par défaut, un nouvel utilisateur est activé


    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public User(String username, String password, String nom, String prenom) {
        this.username = username;
        this.password = password;
        this.nom = nom;
        this.prenom = prenom;
        this.enabled = true; // S'assurer qu'il est activé à la création via ce constructeur

    }
    public void addRole(Role role) {
        this.roles.add(role);
    }
    // Tu peux ajouter des méthodes pratiques si besoin :
    // public boolean isEnabled() {
    //     return enabled;
    // }

    // public void setEnabled(boolean enabled) {
    //     this.enabled = enabled;
    // }
}