/* Shared, deterministic database operations; no DOM/native dependencies. */
(function(root){
'use strict';
const headers=['Group No','Group Name','Name','Sub Name','Repeater Call Sign','Gateway Call Sign','Frequency','Dup','Offset','Mode','TONE','Repeater Tone','RPT1USE','Position','Latitude','Longitude','UTC Offset'];
const norm=s=>String(s??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
const key=r=>(r[4].trim()?r[4].trim().toUpperCase():r[1]+'|'+r[2])+'|'+Number(r[6])+'|'+r[9];
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const clone=x=>JSON.parse(JSON.stringify(x));
const uid=()=>Date.now().toString(36)+'-'+Math.random().toString(36).slice(2)+'-'+Math.random().toString(36).slice(2);
function inferUF(name,geo){
 const aliases={'rio gd do norte':'RN','rio gd do sul':'RS','mato gd do sul':'MS','mato grosso sul':'MS'};
 return geo.states.find(s=>norm(s.name)===norm(name))?.uf||aliases[norm(name)]||'';
}
function migrateState(data,geo){
 const next=clone(data);next.version=2;const groups=new Map((next.groups||[]).map(g=>[String(+g.no),{...g,no:String(+g.no)}]));
 for(const rec of next.records){const r=rec.fields,no=String(+r[0]),old=groups.get(no);
  if(!old||old.name!==r[1])groups.set(no,{no,name:r[1],uf:inferUF(r[1],geo)});
 }
 next.groups=[...groups.values()].sort((a,b)=>+a.no-+b.no);validateGroups(next.groups,geo);return next;
}
function validateGroups(groups,geo){const used=new Set();for(const g of groups){
 if(!/^\d+$/.test(g.no)||+g.no<1||+g.no>50||used.has(+g.no))throw Error('Número de grupo inválido ou já cadastrado.');
 if(!g.name.trim()||g.name.length>16)throw Error('O nome do grupo precisa ter de 1 a 16 caracteres.');
 if(g.uf&&!geo.states.some(s=>s.uf===g.uf))throw Error('Estado do grupo inválido.');used.add(+g.no);
 }}
function addGroup(data,group,geo){const next=migrateState(data,geo);const clean={no:String(+group.no),name:group.name.trim(),uf:group.uf};
 if(!clean.uf)throw Error('Selecione o estado padrão do grupo.');next.groups.push(clean);validateGroups(next.groups,geo);next.groups.sort((a,b)=>+a.no-+b.no);return next;
}
function resolveLocation(rec,geo,groups){const uf=rec.uf||(groups.find(g=>+g.no===+rec.fields[0])?.uf)||inferUF(rec.fields[1],geo);
 const region=geo.states.find(s=>s.uf===uf);const city=region?.cities.find(c=>rec.cityId?c[0]===rec.cityId:norm(c[1])===norm(rec.fields[2]));
 return {uf,cityId:city?.[0]||'',cityName:city?.[1]||rec.fields[2]};
}
function radioName(name){return name.normalize('NFD').replace(/[\u0300-\u036f]/g,'').slice(0,16).trim();}
function distance(lat,lon,a,b){const d=Math.PI/180,x=(a-lat)*d,y=(b-lon)*d,v=Math.sin(x/2)**2+Math.cos(lat*d)*Math.cos(a*d)*Math.sin(y/2)**2;return 6371*2*Math.asin(Math.sqrt(Math.min(1,v)));}
function coordinates(r){return r[13]!=='None'&&r[14]!==''&&r[15]!==''&&Number.isFinite(Number(r[14]))&&Number.isFinite(Number(r[15]));}
function search(records,f){
 const tokens=norm(f.query).trim().split(/\s+/).filter(Boolean);
 return records.filter(rec=>{const r=rec.fields,fr=Number(r[6]);
  if(tokens.length&&!tokens.every(t=>norm(r.join(' ')+' '+(rec.cityName||'')+' '+(rec.uf||'')).includes(t)))return false;
  if(f.group&&r[0]!==f.group)return false;
  if(f.mode&&r[9]!==f.mode)return false;
  if(f.band==='vhf'&&!(fr>=30&&fr<300))return false;
  if(f.band==='uhf'&&!(fr>=300))return false;
  if(f.band==='hf'&&!(fr<30))return false;
  if(f.tone&&r[10]!==f.tone)return false;
  if(f.favorite&&!rec.favorite)return false;
  if(f.min!==''&&f.min!=null&&fr<Number(f.min))return false;
  if(f.max!==''&&f.max!=null&&fr>Number(f.max))return false;
  if(f.radius&&f.origin){if(!coordinates(r)||distance(f.origin.lat,f.origin.lon,Number(r[14]),Number(r[15]))>Number(f.radius))return false;}
  return true;
 }).sort((a,b)=>{
  const x=a.fields,y=b.fields;
  if(f.sort==='distance'&&f.origin){const da=coordinates(x)?distance(f.origin.lat,f.origin.lon,+x[14],+x[15]):Infinity,db=coordinates(y)?distance(f.origin.lat,f.origin.lon,+y[14],+y[15]):Infinity;if(da!==db)return da-db;}
  if(f.sort==='frequency')return +x[6]-y[6]||norm(x[2]).localeCompare(norm(y[2]));
  if(f.sort==='call')return x[4].localeCompare(y[4])||+x[6]-y[6];
  return norm(x[2]).localeCompare(norm(y[2]))||+x[6]-y[6];
 });
}
function planImport(state,incoming,options={}){
 let next=clone(state),stats={added:0,updated:0,unchanged:0,preserved:0,deleted:0},conflicts=[];
 const previous=new Map(state.records.flatMap(r=>[[r.sourceKey||key(r.fields),r],[key(r.fields),r]]));
 if(options.replace){next.records=[];next.tombstones=[];stats.deleted=state.records.length;}
 const lookup=new Map(next.records.flatMap(r=>[[r.sourceKey||key(r.fields),r],[key(r.fields),r]]));
 const deleted=new Set(next.tombstones||[]);
 for(const fields of incoming){const k=key(fields),rec=lookup.get(k);
  if(!options.replace&&deleted.has(k)&&options.preserve!==false){stats.preserved++;continue;}
  if(deleted.has(k)&&options.preserve===false)next.tombstones=next.tombstones.filter(t=>t!==k);
  if(rec){
   if(same(rec.fields,fields)){stats.unchanged++;if(!rec.dirty)rec.base=clone(fields);continue;}
   if(rec.dirty&&options.preserve!==false){stats.preserved++;if(!same(rec.base,fields))conflicts.push(fields[4]||fields[2]);continue;}
   if(rec.fields[1]!==fields[1]||rec.fields[2]!==fields[2]){delete rec.uf;delete rec.cityId;delete rec.cityName;}
   rec.fields=clone(fields);rec.base=clone(fields);rec.sourceKey=k;rec.dirty=false;stats.updated++;
  }else{const old=previous.get(k);next.records.push({id:uid(),fields:clone(fields),base:clone(fields),sourceKey:k,dirty:false,favorite:!!old?.favorite});stats.added++;}
 }
 if(next.records.length>2500)throw Error('A mesclagem excede o limite de 2500 repetidoras. Filtre a base ou use substituição.');
 if(options.github)next.lastSync=new Date().toISOString();
 return {state:next,stats,conflicts};
}
const api={headers,norm,key,clone,uid,distance,coordinates,search,planImport,inferUF,migrateState,validateGroups,addGroup,resolveLocation,radioName};
if(typeof module!=='undefined')module.exports=api;root.RptCore=api;
})(typeof window==='undefined'?globalThis:window);
