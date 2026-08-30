package com.razorpay.recovery.receivable;

import com.razorpay.recovery.receivable.Receivable;
import com.razorpay.recovery.receivable.Receivable.ReceivableStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceivableRepository extends JpaRepository<Receivable, Long> {
    List<Receivable> findByStatusIn(List<ReceivableStatus> statuses);
    List<Receivable> findByStatus(ReceivableStatus status);
}
