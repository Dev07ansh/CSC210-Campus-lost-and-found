package com.csc210.backend.repository;
import com.csc210.backend.model.FoundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FoundItemRepository extends JpaRepository<FoundItem,Long> {
    List<FoundItem> findByReporterId(Long reporterId);
}
