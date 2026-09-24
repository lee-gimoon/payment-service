import { useId } from "react";
import type { Product } from "../types/payment";

const shirtPath = "M68 103 49 111 29 149 57 162 66 140 66 198 Q100 210 134 198 L134 140 143 162 171 149 151 111 132 103 Q100 116 68 103Z";

function Print({ product, clipId, patternId }: { product: Product; clipId: string; patternId: string }) {
  switch (product.artwork) {
    case "sun":
      return <g fill="#f3ad4c" stroke="#f3ad4c" strokeWidth="3">
        <circle cx="100" cy="150" r="17" />
        {Array.from({ length: 8 }, (_, index) => {
          const angle = index * Math.PI / 4;
          return <line key={index} x1={100 + 24 * Math.cos(angle)} y1={150 + 24 * Math.sin(angle)} x2={100 + 32 * Math.cos(angle)} y2={150 + 32 * Math.sin(angle)} />;
        })}
      </g>;
    case "bolt":
      return <path d="M107 125 82 157h19l-9 28 31-39h-20l11-21Z" fill="#f7e65b" />;
    case "stripe":
    case "check":
      return <rect x="20" y="100" width="160" height="110" fill={`url(#${patternId})`} clipPath={`url(#${clipId})`} />;
    case "star":
      return <path d="m100 126 8 18 20 2-15 14 4 20-17-10-17 10 4-20-15-14 20-2Z" fill="#f8e267" />;
    case "pocket":
      return <path d="M111 137h28v31q-14 6-28 0Z M111 143h28" fill="#82bea6" stroke="#558b76" strokeWidth="1.5" />;
    case "ten":
      return <text x="100" y="176" textAnchor="middle" fill="#fff8e8" fontSize="52" fontWeight="800" fontFamily="Arial,sans-serif">10</text>;
    case "moon":
      return <g><circle cx="100" cy="154" r="22" fill="#eae7ff" /><circle cx="110" cy="146" r="21" fill={product.color} /></g>;
    case "smile":
      return <g><circle cx="100" cy="152" r="24" fill="#3d4651" /><circle cx="92" cy="144" r="2.8" fill="#f5d661" /><circle cx="108" cy="144" r="2.8" fill="#f5d661" /><path d="M89 158q11 12 22 0" fill="none" stroke="#f5d661" strokeWidth="2.5" strokeLinecap="round" /></g>;
    case "wave":
      return <path d="M65 148q18-17 36 0t36 0M65 163q18-17 36 0t36 0" fill="none" stroke="#eee7ff" strokeWidth="7" strokeLinecap="round" />;
    default:
      return null;
  }
}

function Shirt({ product, clipId, patternId }: { product: Product; clipId: string; patternId: string }) {
  return <>
    <path d={shirtPath} fill={product.color} stroke="#45465c" strokeWidth="2.8" strokeLinejoin="round" />
    <Print product={product} clipId={clipId} patternId={patternId} />
    <path d="M80 106q20 14 40 0M67 193q33 11 66 0M32 148l25 9M143 157l25-9" fill="none" stroke="#45465c" strokeWidth="1.5" opacity=".4" />
  </>;
}

function ArtDefs({ product, clipId, patternId }: { product: Product; clipId: string; patternId: string }) {
  return <defs>
    <clipPath id={clipId}><path d={shirtPath} /></clipPath>
    <pattern id={patternId} width={product.artwork === "stripe" ? 20 : 30} height={product.artwork === "stripe" ? 20 : 30} patternUnits="userSpaceOnUse">
      {product.artwork === "stripe" ? <><rect width="20" height="20" fill="#e6dcff" /><rect width="8" height="20" fill="#9a85cf" /></> : <><rect width="30" height="30" fill="#ffc7b2" /><rect width="15" height="15" fill="#ee8d80" /><rect x="15" y="15" width="15" height="15" fill="#ee8d80" /></>}
    </pattern>
  </defs>;
}

/** 목록용: 캐릭터 없이 상품 자체의 디자인을 보여준다. */
export function TeeArtwork({ product }: { product: Product }) {
  const id = useId().replace(/:/g, "");
  const clipId = `${id}-clip`;
  const patternId = `${id}-pattern`;
  return <svg viewBox="0 0 200 140" role="img" aria-label={`${product.name} 일러스트`}>
    <ArtDefs product={product} clipId={clipId} patternId={patternId} />
    <ellipse cx="100" cy="127" rx="61" ry="8" fill="#3b4266" opacity=".12" />
    <g transform="translate(0 -97)"><Shirt product={product} clipId={clipId} patternId={patternId} /></g>
  </svg>;
}

/** 한 명의 캐릭터가 현재 선택된 티셔츠를 입은 모습이다. */
export function AvatarArtwork({ product }: { product: Product }) {
  const id = useId().replace(/:/g, "");
  const clipId = `${id}-clip`;
  const patternId = `${id}-pattern`;
  return <svg viewBox="0 0 200 270" role="img" aria-label={`${product.name}을 입은 캐릭터 아바타`}>
    <ArtDefs product={product} clipId={clipId} patternId={patternId} />
    <ellipse cx="100" cy="264" rx="61" ry="9" fill="#3b4266" opacity=".13" />
    <path d="M69 196h62l-3 49-24 2-4-40-4 40-24-2Z" fill="#505b83" stroke="#313a60" strokeWidth="2.5" />
    <path d="M73 239q-10 5-11 15 13 8 32 1l3-13ZM105 242l3 13q19 7 32-1-1-10-11-15Z" fill="#fff" stroke="#363954" strokeWidth="2.5" />
    <path d="M47 130q-5 25 0 44 4 7 11 5l8-42M153 130q5 25 0 44-4 7-11 5l-8-42" fill="#e9aa85" stroke="#8f644f" strokeWidth="2.5" />
    <path d="M89 87v22q10 12 22 0V87Z" fill="#e8ab86" stroke="#8f644f" strokeWidth="2" />
    <Shirt product={product} clipId={clipId} patternId={patternId} />
    <ellipse cx="100" cy="63" rx="39" ry="44" fill="#e9af8b" stroke="#745345" strokeWidth="2" />
    <path d="M60 62Q55 12 102 15q44-3 40 43l-11-14q-17 2-28-11-16 17-40 15Z" fill="#303247" />
    <path d="M64 44q-3 19-1 28M137 43q4 18 1 29" stroke="#303247" strokeWidth="8" strokeLinecap="round" />
    <ellipse cx="84" cy="68" rx="3" ry="4" fill="#303247" /><ellipse cx="116" cy="68" rx="3" ry="4" fill="#303247" />
    <ellipse cx="72" cy="79" rx="7" ry="3" fill="#dc8c80" opacity=".5" /><ellipse cx="128" cy="79" rx="7" ry="3" fill="#dc8c80" opacity=".5" />
    <path d="M94 86q6 5 12 0" stroke="#8b554f" strokeWidth="2" fill="none" strokeLinecap="round" />
  </svg>;
}
