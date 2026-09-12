package com.csc210.backend.repository;
import com.csc210.backend.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MatchRepository extends JpaRepository<Match,Long> {}
