-- 현재 판매 상품은 DB에서 관리한다. 과거 주문의 상품명·단가는 purchase_order_items에 별도로 남는다.
CREATE TABLE products (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    subtitle VARCHAR(100) NOT NULL,
    category VARCHAR(40) NOT NULL,
    price BIGINT NOT NULL CHECK (price > 0),
    color VARCHAR(7) NOT NULL,
    stage VARCHAR(7) NOT NULL,
    artwork VARCHAR(32) NOT NULL,
    badge VARCHAR(32) NOT NULL,
    display_order INTEGER NOT NULL UNIQUE
);

-- 기존 화면의 상품 10종을 최초 데이터로 이관한다. Flyway는 이 파일을 한 번만 적용한다.
INSERT INTO products (id, name, subtitle, category, price, color, stage, artwork, badge, display_order) VALUES
    ('tee-01', '선데이 크루 티', 'IVORY / REGULAR', '베이식', 19000, '#fff5de', '#f9e9d9', 'sun', 'BEST', 1),
    ('tee-02', '볼트 그래픽 티', 'BLACK / OVERSIZE', '그래픽', 28000, '#34364a', '#e3e2ee', 'bolt', 'NEW', 2),
    ('tee-03', '라일락 스트라이프', 'LILAC / RELAXED', '스트라이프', 25000, '#d8cafa', '#eee7fa', 'stripe', '', 3),
    ('tee-04', '블루 스타 티', 'BLUE / BOXY', '그래픽', 27000, '#4e78d7', '#dbe9fa', 'star', '', 4),
    ('tee-05', '민트 포켓 티', 'MINT / REGULAR', '베이식', 23000, '#a2ddc5', '#dff4e9', 'pocket', '', 5),
    ('tee-06', '피치 체크 티', 'PEACH / RELAXED', '스트라이프', 26000, '#ffa98e', '#fbe7df', 'check', 'NEW', 6),
    ('tee-07', '텐 오렌지 티', 'ORANGE / BOXY', '그래픽', 29000, '#f3a44a', '#faeddb', 'ten', '', 7),
    ('tee-08', '미드나잇 문 티', 'CHARCOAL / HEAVY', '그래픽', 31000, '#515469', '#e2e3eb', 'moon', '', 8),
    ('tee-09', '스마일 옐로 티', 'YELLOW / REGULAR', '그래픽', 24000, '#f5d661', '#f8f0cc', 'smile', '', 9),
    ('tee-10', '웨이브 퍼플 티', 'PURPLE / RELAXED', '그래픽', 27000, '#8d87da', '#eae6fa', 'wave', 'LIMITED', 10);
