package com.example.annotationapp.repository;

import com.example.annotationapp.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    Optional<Dataset> findByName(String name);

}