package com.csc210.backend.repository;
import com.csc210.backend.model.FoundItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FoundItemRepository extends JpaRepository<FoundItem,Long> {}
