package com.razorpay.recovery.customer;
import com.razorpay.recovery.subscription.Subscription;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    /** 0.0 (always fails) - 1.0 (always pays on time). Used by the agent as context. */
    private double paymentReliabilityScore;

    /** Customer segment: STANDARD or HIGH_VALUE. HIGH_VALUE gets wider recovery bounds. */
    @Enumerated(EnumType.STRING)
    private CustomerSegment customerSegment = CustomerSegment.STANDARD;

    @JsonIgnore
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<Subscription> subscriptions = new ArrayList<>();

    public enum CustomerSegment {
        STANDARD, HIGH_VALUE
    }
}
