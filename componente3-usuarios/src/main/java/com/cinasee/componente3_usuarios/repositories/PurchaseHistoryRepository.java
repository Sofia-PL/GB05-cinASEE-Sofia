package com.cinasee.componente3_usuarios.repositories;

import com.cinasee.componente3_usuarios.models.PurchaseHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseHistoryRepository extends JpaRepository<PurchaseHistory, Long> {

    List<PurchaseHistory> findByUserIdOrderByPurchaseDateDesc(Long userId);

    boolean existsByReservationId(Long reservationId);
}
