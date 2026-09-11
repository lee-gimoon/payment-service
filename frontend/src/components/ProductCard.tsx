export function ProductCard() {
  return (
    <section className="product-card" aria-label="판매 상품">
      <div className="product-art">
        <span className="product-tag">THE EVERYDAY TEE</span>
        <svg viewBox="0 0 360 300" role="img" aria-label="흰색 기본 티셔츠">
          <ellipse cx="180" cy="275" rx="105" ry="10" fill="#dfd7cb" />
          <path
            d="M127 42 81 63 30 134 79 167 107 129 102 260Q180 277 258 260L253 129 281 167 330 134 279 63 233 42Q180 66 127 42Z"
            fill="#fffdf8"
            stroke="#cec6b9"
            strokeWidth="2"
          />
          <path
            d="M146 48Q180 94 214 48M127 42 146 48M214 48 233 42"
            fill="none"
            stroke="#cec6b9"
            strokeWidth="3"
          />
          <path
            d="M104 248Q180 262 256 248M45 135 78 156M282 156 315 135"
            fill="none"
            stroke="#e6dfd4"
            strokeWidth="2"
          />
        </svg>
        <span className="art-caption">매일의 기본이 되는 한 장.</span>
      </div>

      <div className="product-info">
        <div>
          <h2>에브리데이 티셔츠</h2>
          <p>티셔츠 1장 · 단일 상품</p>
        </div>
        <strong>
          10,000<span>원</span>
        </strong>
      </div>
    </section>
  );
}
