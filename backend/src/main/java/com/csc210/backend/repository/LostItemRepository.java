package com.csc210.backend.repository;
import com.csc210.backend.model.LostItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LostItemRepository extends JpaRepository<LostItem,Long> {}
