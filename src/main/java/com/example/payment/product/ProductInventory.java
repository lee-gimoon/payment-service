package com.example.payment.product;

import com.example.payment.api.error.ApiException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이즈별 재고. 주문은 승인 슬롯을 잡을 때 재고를 가져가고, 승인 실패가 확인돼 슬롯을 돌려줄 때 재고도 돌려준다.
 * 주문 생성과 결제 시도 시작에서는 확인만 하므로, 재고가 빠져 있는 기간은 승인 슬롯을 잡고 있는 기간과 같다.
 */
@Service
public class ProductInventory {
    private static final Comparator<Line> LOCK_ORDER = Comparator.comparing(Line::productId).thenComparing(Line::size);

    private final ProductStockRepository stocks;

    public ProductInventory(ProductStockRepository stocks) {
        this.stocks = stocks;
    }

    /** 상품별·사이즈별 남은 수량. 재고 행이 없는 사이즈는 담지 않으며 0으로 본다. */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Integer>> remaining(Collection<String> productIds) {
        Map<String, Map<String, Integer>> remaining = new HashMap<>();
        for (ProductStock stock : stocks.findAllByProductIdIn(productIds)) {
            remaining.computeIfAbsent(stock.getProductId(), id -> new HashMap<>())
                    .put(stock.getSize(), stock.getQuantity());
        }
        return remaining;
    }

    /** 재고를 잡지 않고 확인만 한다. 결제창을 열기 전에 품절을 알리기 위한 것이며, 최종 판단은 take가 한다. */
    @Transactional(readOnly = true)
    public void requireAvailable(List<Line> lines) {
        Map<String, Map<String, Integer>> remaining = remaining(lines.stream().map(Line::productId).toList());
        for (Line line : lines) {
            if (remaining.getOrDefault(line.productId(), Map.of()).getOrDefault(line.size(), 0) < line.quantity()) {
                throw outOfStock(line);
            }
        }
    }

    /**
     * 주문 잠금을 잡은 호출자의 트랜잭션 안에서만 뺀다.
     * 한 줄이라도 부족하면 예외로 트랜잭션 전체를 롤백해 앞서 뺀 줄도 되돌린다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void take(List<Line> lines) {
        // 같은 순서로 재고 행을 잠가 여러 상품을 담은 주문끼리 교착되지 않게 한다.
        for (Line line : lines.stream().sorted(LOCK_ORDER).toList()) {
            if (stocks.take(line.productId(), line.size(), line.quantity()) == 0) {
                throw outOfStock(line);
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void putBack(List<Line> lines) {
        for (Line line : lines.stream().sorted(LOCK_ORDER).toList()) {
            stocks.putBack(line.productId(), line.size(), line.quantity());
        }
    }

    private static ApiException outOfStock(Line line) {
        return new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK",
                line.productName() + " " + line.size() + " 사이즈의 재고가 부족합니다.");
    }

    /** 재고를 확인하거나 옮길 주문 한 줄. 상품명은 품절 안내에만 쓴다. */
    public record Line(String productId, String productName, String size, int quantity) {}
}
