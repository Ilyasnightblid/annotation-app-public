package com.example.annotationapp.service;

import com.example.annotationapp.entity.Role;
import com.example.annotationapp.entity.User;
import com.example.annotationapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Utilise la nouvelle méthode du repository pour charger seulement les utilisateurs actifs
        User user = userRepository.findByUsernameAndEnabledTrue(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username + " or user is disabled."));

        return new org.springframework.security.core.userdetails.User(user.getUsername(),
                user.getPassword(),
                user.isEnabled(), // Utilise le champ enabled de l'entité User
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                mapRolesToAuthorities(user.getRoles()));
    }



    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Collection<Role> roles) {    return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());
    }
}