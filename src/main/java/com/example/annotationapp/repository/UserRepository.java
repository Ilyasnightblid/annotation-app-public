package com.example.annotationapp.repository;

import com.example.annotationapp.entity.Role;
import com.example.annotationapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByRolesContaining(Role role);
    // Pour trouver un utilisateur actif par son username (utile pour le login)
    Optional<User> findByUsernameAndEnabledTrue(String username);
    // Pour lister les annotateurs actifs
    List<User> findByRolesContainingAndEnabledTrue(Role role);
}