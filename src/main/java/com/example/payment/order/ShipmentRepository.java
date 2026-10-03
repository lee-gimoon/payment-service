package com.example.payment.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, String> {
    Optional<Shipment> findByOrderId(String orderId);
    List<Shipment> findByOrderIdIn(Collection<String> orderIds);
    long countByStatus(ShipmentStatus status);
}
