package com.razorpay.recovery.customer;

import com.razorpay.recovery.customer.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
