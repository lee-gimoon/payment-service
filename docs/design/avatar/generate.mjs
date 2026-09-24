import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const dir = dirname(fileURLToPath(import.meta.url));
const C = { ink:"#23243a", blue:"#4258e7", sky:"#e9eeff", cream:"#fffdf7", line:"#dce0eb", muted:"#70748d", coral:"#ff816d", mint:"#d7f3e8", white:"#fff" };
const products = [
  {id:"01",name:"선데이 크루 티",meta:"IVORY / REGULAR",price:"19,000원",color:"#fff5de",stage:"#f9e9d9",style:"sun",tag:"BEST"},
  {id:"02",name:"볼트 그래픽 티",meta:"BLACK / OVERSIZE",price:"28,000원",color:"#34364a",stage:"#e3e2ee",style:"bolt",tag:"NEW"},
  {id:"03",name:"라일락 스트라이프",meta:"LILAC / RELAXED",price:"25,000원",color:"#d8cafa",stage:"#eee7fa",style:"stripe",tag:""},
  {id:"04",name:"블루 스타 티",meta:"BLUE / BOXY",price:"27,000원",color:"#4e78d7",stage:"#dbe9fa",style:"star",tag:""},
  {id:"05",name:"민트 포켓 티",meta:"MINT / REGULAR",price:"23,000원",color:"#a2ddc5",stage:"#dff4e9",style:"pocket",tag:""},
  {id:"06",name:"피치 체크 티",meta:"PEACH / RELAXED",price:"26,000원",color:"#ffa98e",stage:"#fbe7df",style:"check",tag:"NEW"},
  {id:"07",name:"텐 오렌지 티",meta:"ORANGE / BOXY",price:"29,000원",color:"#f3a44a",stage:"#faeddb",style:"ten",tag:""},
  {id:"08",name:"미드나잇 문 티",meta:"CHARCOAL / HEAVY",price:"31,000원",color:"#515469",stage:"#e2e3eb",style:"moon",tag:""},
  {id:"09",name:"스마일 옐로 티",meta:"YELLOW / REGULAR",price:"24,000원",color:"#f5d661",stage:"#f8f0cc",style:"smile",tag:""},
  {id:"10",name:"웨이브 퍼플 티",meta:"PURPLE / RELAXED",price:"27,000원",color:"#8d87da",stage:"#eae6fa",style:"wave",tag:"LIMITED"}
];

const esc = s => String(s).replace(/[&<>\"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"})[c]);
const rect=(x,y,w,h,fill,r=0,stroke="none",sw=1)=>`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}"/>`;
const txt=(x,y,s,size=14,color=C.ink,weight=500,attr="")=>`<text x="${x}" y="${y}" fill="${color}" font-family="Arial,Malgun Gothic,Noto Sans KR,sans-serif" font-size="${size}" font-weight="${weight}" ${attr}>${esc(s)}</text>`;
const line=(x1,y1,x2,y2,color=C.line,sw=1)=>`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="${sw}"/>`;
const defs=`<defs>
 <pattern id="stripePattern" width="20" height="20" patternUnits="userSpaceOnUse"><rect width="20" height="20" fill="#e6dcff"/><rect width="8" height="20" fill="#9a85cf"/></pattern>
 <pattern id="checkPattern" width="30" height="30" patternUnits="userSpaceOnUse"><rect width="30" height="30" fill="#ffc7b2"/><rect width="15" height="15" fill="#ee8d80"/><rect x="15" y="15" width="15" height="15" fill="#ee8d80"/></pattern>
 <linearGradient id="heroGradient" x2="1" y2="1"><stop stop-color="#e5edff"/><stop offset="1" stop-color="#f9e8ef"/></linearGradient>
 <filter id="softShadow" x="-30%" y="-30%" width="160%" height="180%"><feDropShadow dx="0" dy="9" stdDeviation="7" flood-color="#404366" flood-opacity=".16"/></filter>
 </defs>`;
const wrap=(w,h,title,body)=>`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(title)}"><title>${esc(title)}</title>${defs}${body}</svg>`;
const shirtPath="M68 103 49 111 29 149 57 162 66 140 66 198 Q100 210 134 198 L134 140 143 162 171 149 151 111 132 103 Q100 116 68 103Z";

function printArt(p, clipId) {
  switch(p.style) {
    case "sun": return `<circle cx="100" cy="150" r="18" fill="#f3ad4c"/><g stroke="#f3ad4c" stroke-width="3">${Array.from({length:8},(_,i)=>{const a=i*Math.PI/4;return `<line x1="${100+24*Math.cos(a)}" y1="${150+24*Math.sin(a)}" x2="${100+31*Math.cos(a)}" y2="${150+31*Math.sin(a)}"/>`;}).join("")}</g>`;
    case "bolt": return `<path d="M107 125 82 157h19l-9 28 31-39h-20l11-21Z" fill="#f7e65b"/>`;
    case "stripe": return `<rect x="20" y="100" width="160" height="110" fill="url(#stripePattern)" clip-path="url(#${clipId})"/>`;
    case "star": return `<path d="m100 126 8 18 20 2-15 14 4 20-17-10-17 10 4-20-15-14 20-2Z" fill="#f8e267"/>`;
    case "pocket": return `<path d="M111 137h28v31q-14 6-28 0Z" fill="#82bea6" stroke="#558b76" stroke-width="1.5"/><path d="M111 143h28" stroke="#558b76"/>`;
    case "check": return `<rect x="20" y="100" width="160" height="110" fill="url(#checkPattern)" clip-path="url(#${clipId})"/>`;
    case "ten": return `${txt(100,177,"10",53,"#fff8e8",800,'text-anchor="middle" letter-spacing="-5"')}`;
    case "moon": return `<circle cx="100" cy="154" r="22" fill="#eae7ff"/><circle cx="110" cy="146" r="21" fill="#515469"/>`;
    case "smile": return `<circle cx="100" cy="152" r="24" fill="#3d4651"/><circle cx="92" cy="144" r="2.8" fill="#f5d661"/><circle cx="108" cy="144" r="2.8" fill="#f5d661"/><path d="M89 158q11 12 22 0" fill="none" stroke="#f5d661" stroke-width="2.5" stroke-linecap="round"/>`;
    case "wave": return `<path d="M65 148q18-17 36 0t36 0M65 163q18-17 36 0t36 0" fill="none" stroke="#eee7ff" stroke-width="7" stroke-linecap="round"/>`;
  }
  return "";
}

function avatar(p,x,y,scale=1) {
  const clipId=`garment-${p.id}-${Math.round(x)}-${Math.round(y)}`;
  return `<g transform="translate(${x} ${y}) scale(${scale})">
  <ellipse cx="100" cy="266" rx="61" ry="10" fill="#3b4266" opacity=".13"/>
  <path d="M69 196h62l-3 49-24 2-4-40-4 40-24-2Z" fill="#505b83" stroke="#313a60" stroke-width="2.5"/>
  <path d="M73 239q-10 5-11 15 13 8 32 1l3-13ZM105 242l3 13q19 7 32-1-1-10-11-15Z" fill="#fff" stroke="#363954" stroke-width="2.5"/>
  <path d="M47 130q-5 25 0 44 4 7 11 5l8-42M153 130q5 25 0 44-4 7-11 5l-8-42" fill="#e9aa85" stroke="#8f644f" stroke-width="2.5"/>
  <path d="M89 87v22q10 12 22 0V87Z" fill="#e8ab86" stroke="#8f644f" stroke-width="2"/>
  <defs><clipPath id="${clipId}"><path d="${shirtPath}"/></clipPath></defs>
  <path d="${shirtPath}" fill="${p.color}" stroke="#45465c" stroke-width="2.8" stroke-linejoin="round"/>
  ${printArt(p,clipId)}
  <path d="M80 106q20 14 40 0M67 193q33 11 66 0M32 148l25 9M143 157l25-9" fill="none" stroke="#45465c" stroke-width="1.5" opacity=".4"/>
  <ellipse cx="100" cy="63" rx="39" ry="44" fill="#e9af8b" stroke="#745345" stroke-width="2"/>
  <path d="M60 62Q55 12 102 15q44-3 40 43l-11-14q-17 2-28-11-16 17-40 15Z" fill="#303247"/>
  <path d="M64 44q-3 19-1 28M137 43q4 18 1 29" stroke="#303247" stroke-width="8" stroke-linecap="round"/>
  <ellipse cx="84" cy="68" rx="3" ry="4" fill="#303247"/><ellipse cx="116" cy="68" rx="3" ry="4" fill="#303247"/>
  <ellipse cx="72" cy="79" rx="7" ry="3" fill="#dc8c80" opacity=".5"/><ellipse cx="128" cy="79" rx="7" ry="3" fill="#dc8c80" opacity=".5"/>
  <path d="M94 86q6 5 12 0" stroke="#8b554f" stroke-width="2" fill="none" stroke-linecap="round"/>
  </g>`;
}

function garment(p,x,y,scale=1) {
  const clipId=`product-${p.id}-${Math.round(x)}-${Math.round(y)}`;
  return `<g transform="translate(${x} ${y}) scale(${scale})">
    <ellipse cx="100" cy="128" rx="65" ry="9" fill="#3b4266" opacity=".12"/>
    <g transform="translate(0 -100)">
      <defs><clipPath id="${clipId}"><path d="${shirtPath}"/></clipPath></defs>
      <path d="${shirtPath}" fill="${p.color}" stroke="#45465c" stroke-width="2.8" stroke-linejoin="round" filter="url(#softShadow)"/>
      ${printArt(p,clipId)}
      <path d="M80 106q20 14 40 0M67 193q33 11 66 0M32 148l25 9M143 157l25-9" fill="none" stroke="#45465c" stroke-width="1.5" opacity=".4"/>
    </g>
  </g>`;
}

function productCard(p,x,y,w,mobile=false) {
  const stageH=mobile?190:257, s=mobile?1.05:1.45;
  let out=rect(x,y,w,stageH,p.stage,mobile?14:18);
  out+=`<circle cx="${x+w/2}" cy="${y+stageH*.51}" r="${mobile?73:101}" fill="#fff" opacity=".37"/>`;
  out+=garment(p,x+(w-200*s)/2,y+(mobile?29:39),s);
  out+=rect(x+11,y+11,38,24,C.white,12)+txt(x+30,y+28,p.id,11,C.ink,800,'text-anchor="middle"');
  if(p.tag) out+=rect(x+w-(mobile?62:74),y+11,mobile?51:63,24,C.coral,12)+txt(x+w-(mobile?36.5:42.5),y+28,p.tag,mobile?9:10,C.white,800,'text-anchor="middle"');
  out+=txt(x,y+stageH+(mobile?21:27),p.meta,mobile?8:10,C.muted,700,'letter-spacing="1"');
  out+=txt(x,y+stageH+(mobile?43:57),p.name,mobile?13:17,C.ink,800);
  out+=txt(x,y+stageH+(mobile?64:83),p.price,mobile?13:16,C.ink,700);
  out+=`<circle cx="${x+w-12}" cy="${y+stageH+(mobile?59:78)}" r="7" fill="${p.color}" stroke="#9ca0ad" stroke-width=".7"/>`;
  return out;
}

function desktop(){
  const W=1440,H=1720;
  let s=rect(0,0,W,H,C.cream);
  s+=rect(0,0,W,31,C.ink)+txt(720,21,"시안용 스토어 · 테스트 결제는 실제 청구되지 않습니다",11,C.white,600,'text-anchor="middle"');
  s+=rect(0,31,W,88,C.cream)+txt(70,89,"MODO",35,C.ink,900,'letter-spacing="-2"')+txt(189,89,"CLUB",13,C.blue,800,'letter-spacing="2"');
  s+=txt(536,84,"전체 상품",15,C.ink,700)+txt(649,84,"베이식",15,C.muted,600)+txt(750,84,"그래픽",15,C.muted,600)+txt(851,84,"컬렉션",15,C.muted,600);
  s+=txt(1177,84,"검색",14,C.ink,600)+txt(1325,84,"장바구니  0",14,C.ink,700,'text-anchor="end"')+line(0,119,W,119);
  s+=rect(30,145,1380,450,"url(#heroGradient)",26);
  s+=`<circle cx="1112" cy="333" r="215" fill="#d9e3ff"/><circle cx="1225" cy="500" r="100" fill="#f8d9d8" opacity=".7"/>`;
  s+=txt(84,225,"WELCOME TO MODO CLUB",14,C.blue,800,'letter-spacing="3"');
  s+=txt(80,313,"오늘의 티,",64,C.ink,900,'letter-spacing="-3"')+txt(80,390,"오늘의 나.",64,C.ink,900,'letter-spacing="-3"');
  s+=txt(84,435,"원하는 옷을 고르고,",19,C.muted,600)+txt(84,466,"캐릭터 아바타에 입혀보세요.",19,C.muted,600);
  s+=rect(84,502,150,52,C.blue,16)+txt(159,536,"룩 보기",16,C.white,800,'text-anchor="middle"');
  s+=rect(938,188,263,359,"#fff",28,"#dfe3f4",1);
  s+=`<circle cx="1069" cy="351" r="125" fill="${products[0].stage}"/>`;
  s+=avatar(products[0],967,216,1.02);
  s+=txt(1069,519,"MY AVATAR",12,C.ink,800,'text-anchor="middle" letter-spacing="2"');
  s+=txt(70,664,"T-SHIRT SHOP",13,C.blue,800,'letter-spacing="2"');
  s+=txt(68,719,"티셔츠",35,C.ink,900)+txt(218,718,"10개 상품",17,C.muted,600);
  s+=txt(70,753,"마음에 드는 디자인을 골라보세요. 고른 옷은 아바타에 입혀볼 수 있어요.",16,C.muted,500);
  const chips=["전체 10","베이식","그래픽","스트라이프","컬러풀"];
  let cx=70;chips.forEach((chip,i)=>{const w=[91,83,83,111,93][i];s+=rect(cx,781,w,37,i===0?C.ink:C.white,18,i===0?C.ink:C.line)+txt(cx+w/2,806,chip,13,i===0?C.white:C.ink,700,'text-anchor="middle"');cx+=w+9;});
  s+=txt(1370,806,"추천순  ↓",13,C.ink,700,'text-anchor="end"');
  s+=line(70,837,1370,837);
  products.forEach((p,i)=>s+=productCard(p,70+(i%5)*263,865+Math.floor(i/5)*375,248));
  s+=rect(70,1628,1300,58,C.sky,16)+txt(97,1665,"MODO CLUB",15,C.blue,900,'letter-spacing="1"')+txt(300,1665,"이 화면의 상품명과 가격은 시안용 예시입니다.",13,C.ink,500);
  return wrap(W,H,"MODO CLUB 아바타 티셔츠 10종 데스크톱 상품 목록",s);
}

function mobile(){
  const W=390,H=2470;
  let s=rect(0,0,W,H,C.cream);
  s+=rect(0,0,W,26,C.ink)+txt(195,18,"시안용 스토어 · 테스트 결제는 실제 청구되지 않습니다",8,C.white,600,'text-anchor="middle"');
  s+=rect(0,26,W,68,C.cream)+txt(20,72,"MODO",28,C.ink,900,'letter-spacing="-1"')+txt(116,72,"CLUB",10,C.blue,800,'letter-spacing="1"')+txt(327,70,"검색",11,C.ink,600)+txt(371,71,"≡",23,C.ink,600,'text-anchor="end"');
  s+=rect(12,106,366,492,"url(#heroGradient)",20);
  s+=txt(32,151,"WELCOME TO MODO CLUB",9,C.blue,800,'letter-spacing="1.6"');
  s+=txt(30,202,"오늘의 티,",37,C.ink,900,'letter-spacing="-1.3"')+txt(30,248,"오늘의 나.",37,C.ink,900,'letter-spacing="-1.3"');
  s+=txt(32,277,"옷을 골라 캐릭터 아바타에 입혀보세요.",12,C.muted,600);
  s+=rect(32,297,112,37,C.blue,12)+txt(88,321,"룩 보기",11,C.white,800,'text-anchor="middle"');
  s+=rect(89,355,207,220,"#fff",20,"#e2e5f5")+`<circle cx="192" cy="452" r="92" fill="#f8e7db"/>`+avatar(products[0],117,362,.76);
  s+=txt(20,656,"T-SHIRT SHOP",10,C.blue,800,'letter-spacing="1.5"');
  s+=txt(18,706,"티셔츠",28,C.ink,900)+txt(127,705,"10개 상품",13,C.muted,600);
  s+=txt(20,752,"마음에 드는 디자인을 골라보세요.",12,C.muted,500);
  const chips=["전체 10","베이식","그래픽"];[20,99,177].forEach((x,i)=>{s+=rect(x,773,71,32,i===0?C.ink:C.white,15,i===0?C.ink:C.line)+txt(x+35.5,794,chips[i],10,i===0?C.white:C.ink,700,'text-anchor="middle"');});
  s+=txt(370,794,"추천순 ↓",10,C.ink,700,'text-anchor="end"')+line(20,821,370,821);
  products.forEach((p,i)=>s+=productCard(p,i%2===0?20:205,843+Math.floor(i/2)*306,165,true));
  s+=rect(20,2405,350,43,C.sky,12)+txt(195,2432,"상품명·가격은 시안용 예시입니다.",11,C.ink,600,'text-anchor="middle"');
  return wrap(W,H,"MODO CLUB 아바타 티셔츠 10종 모바일 상품 목록",s);
}

writeFileSync(join(dir,"catalog-desktop.svg"),desktop());
writeFileSync(join(dir,"catalog-mobile.svg"),mobile());
console.log("Generated MODO CLUB avatar catalog mockups.");
