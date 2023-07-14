package lk.lnas.ims.repos;

import lk.lnas.ims.domain.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
    List<PurchaseItem> findByPurchaseId(Long purchaseId);
}
