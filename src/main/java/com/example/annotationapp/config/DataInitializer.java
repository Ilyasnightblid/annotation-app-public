package com.example.annotationapp.config;

import com.example.annotationapp.entity.Role;
import com.example.annotationapp.entity.User;
import com.example.annotationapp.repository.RoleRepository;
import com.example.annotationapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // IMPORTANT

import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional // Enveloppe toute la méthode run dans une seule transaction
    public void run(String... args) throws Exception {
        // Créer les rôles s'ils n'existent pas
        Role adminRole = roleRepository.findByName("ROLE_ADMIN");
        if (adminRole == null) {
            adminRole = new Role("ROLE_ADMIN");
            adminRole = roleRepository.save(adminRole); // S'assurer qu'il est managé et a un ID
        }

        Role annotatorRole = roleRepository.findByName("ROLE_ANNOTATOR");
        if (annotatorRole == null) {
            annotatorRole = new Role("ROLE_ANNOTATOR");
            annotatorRole = roleRepository.save(annotatorRole); // S'assurer qu'il est managé et a un ID
        }

        // Créer un utilisateur admin par défaut s'il n'existe pas
        if (userRepository.findByUsername("admin").isEmpty()) {
            User adminUser = new User("admin", passwordEncoder.encode("admin123"), "Admin", "User");

            // Récupérer les rôles managés avant de les assigner
            Set<Role> adminRolesSet = new HashSet<>();
            Role fetchedAdminRole = roleRepository.findByName("ROLE_ADMIN"); // Récupère à nouveau pour être sûr
            if (fetchedAdminRole != null) {
                adminRolesSet.add(fetchedAdminRole);
            } else {
                // Cela ne devrait pas arriver si la logique ci-dessus est correcte
                throw new RuntimeException("ROLE_ADMIN not found after attempting to create it.");
            }
            adminUser.setRoles(adminRolesSet);
            userRepository.save(adminUser); // La ligne 48 est ici
            System.out.println(">>> Created ADMIN user: admin / admin123");
        }

        // Créer un utilisateur annotateur de test s'il n'existe pas
        if (userRepository.findByUsername("annotator1").isEmpty()) {
            User testAnnotator = new User("annotator1", passwordEncoder.encode("annotator123"), "Test", "Annotator");

            Set<Role> annotatorRolesSet = new HashSet<>();
            Role fetchedAnnotatorRole = roleRepository.findByName("ROLE_ANNOTATOR"); // Récupère à nouveau
            if (fetchedAnnotatorRole != null) {
                annotatorRolesSet.add(fetchedAnnotatorRole);
            } else {
                throw new RuntimeException("ROLE_ANNOTATOR not found after attempting to create it.");
            }
            testAnnotator.setRoles(annotatorRolesSet);
            userRepository.save(testAnnotator);
            System.out.println(">>> Created ANNOTATOR user: annotator1 / annotator123");
        }
    }
}