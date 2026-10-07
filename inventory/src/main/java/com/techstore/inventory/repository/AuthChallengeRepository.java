package com.techstore.inventory.repository;
import com.techstore.inventory.model.AuthChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, String> {}