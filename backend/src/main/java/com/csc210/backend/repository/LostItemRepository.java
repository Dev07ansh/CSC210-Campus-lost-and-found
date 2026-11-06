package com.csc210.backend.repository;
import com.csc210.backend.model.LostItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LostItemRepository extends JpaRepository<LostItem,Long> {
    List<LostItem> findByReporterId(Long reporterId);
}
