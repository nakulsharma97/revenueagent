package com.razorpay.recovery.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);
    
    List<AuditEvent> findByActorOrderByTimestampDesc(AuditEvent.Actor actor);
    
    List<AuditEvent> findByBatchIdOrderByTimestampAsc(String batchId);
    
    @Query("SELECT a FROM AuditEvent a ORDER BY a.timestamp DESC")
    List<AuditEvent> findRecentEvents();
}
