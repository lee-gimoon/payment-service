import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { getProducts } from "../api/paymentApi";
import type { CartItem, Product, ShirtSize } from "../types/payment";
import { readLocalValue, writeLocalValue } from "./storage";

const CART_KEY = "modoCart";
const SIZES = new Set<ShirtSize>(["S", "M", "L", "XL"]);

function readCart(): CartItem[] {
  try {
    const value: unknown = JSON.parse(readLocalValue(CART_KEY) ?? "[]");
    if (!Array.isArray(value)) return [];
    return value.filter((item): item is CartItem =>
      item !== null && typeof item === "object" &&
      typeof item.productId === "string" && SIZES.has(item.size) &&
      Number.isInteger(item.quantity) && item.quantity >= 1 && item.quantity <= 10
    );
  } catch {
    return [];
  }
}

interface ShopState {
  products: Product[];
  loading: boolean;
  catalogError: string;
  reloadProducts: () => void;
  cart: CartItem[];
  cartCount: number;
  addToCart: (productId: string, size: ShirtSize) => boolean;
  changeQuantity: (productId: string, size: ShirtSize, delta: number) => void;
  removeFromCart: (productId: string, size: ShirtSize) => void;
  clearCart: () => void;
}

const ShopContext = createContext<ShopState | null>(null);

export function ShopProvider({ children }: { children: ReactNode }) {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [catalogError, setCatalogError] = useState("");
  const [reloadToken, setReloadToken] = useState(0);
  const [cart, setCart] = useState<CartItem[]>(readCart);

  useEffect(() => {
    let active = true;
    setLoading(true);
    getProducts().then(value => {
      if (active) {
        setProducts(value);
        setCatalogError("");
      }
    }).catch(() => {
      if (active) setCatalogError("상품 목록을 불러오지 못했습니다. 서버 연결을 확인해주세요.");
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [reloadToken]);

  useEffect(() => { writeLocalValue(CART_KEY, JSON.stringify(cart)); }, [cart]);

  function addToCart(productId: string, size: ShirtSize): boolean {
    const existing = cart.find(item => item.productId === productId && item.size === size);
    if (existing && existing.quantity >= 10) return false;
    setCart(previous => {
      const found = previous.find(item => item.productId === productId && item.size === size);
      return found
        ? previous.map(item => item === found ? { ...item, quantity: Math.min(10, item.quantity + 1) } : item)
        : [...previous, { productId, size, quantity: 1 }];
    });
    return true;
  }

  function changeQuantity(productId: string, size: ShirtSize, delta: number) {
    setCart(previous => previous.flatMap(item => {
      if (item.productId !== productId || item.size !== size) return [item];
      const quantity = Math.min(10, item.quantity + delta);
      return quantity > 0 ? [{ ...item, quantity }] : [];
    }));
  }

  function removeFromCart(productId: string, size: ShirtSize) {
    setCart(previous => previous.filter(item => item.productId !== productId || item.size !== size));
  }

  return <ShopContext.Provider value={{
    products, loading, catalogError, reloadProducts: () => setReloadToken(value => value + 1),
    cart, cartCount: cart.reduce((sum, item) => sum + item.quantity, 0),
    addToCart, changeQuantity, removeFromCart, clearCart: () => setCart([])
  }}>{children}</ShopContext.Provider>;
}

export function useShop(): ShopState {
  const state = useContext(ShopContext);
  if (!state) throw new Error("ShopProvider가 필요합니다.");
  return state;
}
