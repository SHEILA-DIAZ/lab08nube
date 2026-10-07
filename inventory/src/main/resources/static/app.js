'use strict';
const $=id=>document.getElementById(id);
let token=sessionStorage.getItem('techstoreToken'), me=null, products=[], challenge=null, reportData=null;
const money=n=>new Intl.NumberFormat('es-PE',{style:'currency',currency:'PEN'}).format(n);
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function notice(message,error=false){$('notice').textContent=message;$('notice').className=error?'error':'';$('notice').hidden=false;}
function clearSession(){
 sessionStorage.removeItem('techstoreToken');token=null;me=null;challenge=null;products=[];reportData=null;
 $('dashboard').hidden=true;$('auth').hidden=false;$('logout').hidden=true;$('identity').textContent='';
 $('users').hidden=true;$('report').hidden=true;$('mfaSetup').hidden=true;$('secret').textContent='';
 $('otpLink').removeAttribute('href');$('userRows').replaceChildren();$('productRows').replaceChildren();
 $('productForm').reset();$('productForm').elements.id.value='';$('search').value='';$('enableMfaForm').reset();$('mfaForm').reset();authTab();
}
async function api(path,method='GET',data){
 const requestToken=token;
 const headers={};if(token)headers.Authorization='Bearer '+token;if(data)headers['Content-Type']='application/json';
 const res=await fetch('/api'+path,{method,headers,body:data?JSON.stringify(data):undefined});
 const body=res.status===204?null:await res.json().catch(()=>null);
 if(requestToken && requestToken!==token)throw Error('La sesión cambió. Vuelve a realizar la acción.');
 if(!res.ok){
  if(res.status===423 || (res.status===401 && token && !path.startsWith('/auth/')))clearSession();
  throw Error(body?.error||body?.detail|| (body?Object.values(body).join(' · '):res.status===403?'No tienes permiso para esta operación':res.status===401?'Credenciales inválidas o sesión vencida':'Error '+res.status));
 }
 return body;
}
function bind(id,event,fn){
 const element=$(id);let busy=false;
 element.addEventListener(event,async e=>{
  e.preventDefault();if(busy)return;
  const button=e.submitter||(e.target instanceof Element?e.target.closest('button'):null);
  busy=true;if(button)button.disabled=true;
  try{await fn(e);}catch(err){notice(err.message,true);}
  finally{busy=false;if(button)button.disabled=false;}
 });
}
function data(form){return Object.fromEntries(new FormData(form));}
function authTab(register=false){
 $('loginForm').hidden=register;$('registerForm').hidden=!register;$('mfaForm').hidden=true;$('social').hidden=false;
 $('loginTab').classList.toggle('active',!register);$('registerTab').classList.toggle('active',register);challenge=null;
}
async function accept(result){
 if(result.mfaRequired){clearSession();challenge=result.challengeToken;$('loginForm').hidden=true;$('registerForm').hidden=true;$('mfaForm').hidden=false;$('social').hidden=true;$('mfaForm').reset();return;}
 token=result.token;sessionStorage.setItem('techstoreToken',token);await dashboard();
}
async function dashboard(){
 me=await api('/me');$('auth').hidden=true;$('dashboard').hidden=false;$('logout').hidden=false;
 $('identity').textContent=me.fullName+' · '+me.role;
 $('scope').textContent=['ADMIN','AUDITOR'].includes(me.role)?'Consulta de todas las tiendas':'Tienda asignada: '+me.store;
 const manage=['ADMIN','GERENTE'].includes(me.role);
 $('productForm').hidden=!manage;$('reportButton').hidden=!['ADMIN','GERENTE','AUDITOR'].includes(me.role);
 $('usersButton').hidden=!['ADMIN','AUDITOR'].includes(me.role);
 $('mfaStatus').textContent=me.mfaEnabled?'Verificación en dos pasos activa':'Protege tu acceso con un autenticador TOTP.';
 $('setupMfa').hidden=me.mfaEnabled;$('mfaSetup').hidden=true;$('secret').textContent='';
 $('otpLink').removeAttribute('href');$('users').hidden=true;$('report').hidden=true;reportData=null;
 resetProduct();await loadProducts();
}
function resetProduct(){$('productForm').reset();$('productForm').elements.id.value='';$('productForm').elements.store.value=me.store;$('productForm').elements.store.readOnly=me.role==='GERENTE';$('productFormTitle').textContent='Nuevo producto';}
async function loadProducts(){
 products=await api('/products');$('productCount').textContent=products.length;
 $('report').hidden=true;reportData=null;
 $('stockCount').textContent=products.reduce((sum,p)=>sum+p.stock,0);
 $('valueCount').textContent=money(products.reduce((sum,p)=>sum+Number(p.price)*p.stock,0));
 renderProducts();
}
function renderProducts(){
 const query=$('search').value.toLowerCase();
 const filtered=products.filter(p=>(p.name+' '+p.store).toLowerCase().includes(query));
 $('empty').hidden=filtered.length>0;
 $('productRows').innerHTML=filtered.map(p=>'<tr><td><strong>'+escape(p.name)+'</strong><small>#'+p.id+'</small></td><td>'+escape(p.store)+'</td><td>'+money(p.price)+'</td><td><span class="badge">'+p.stock+'</span></td><td>'+
 (['ADMIN','GERENTE'].includes(me.role)?'<button data-action="edit" data-id="'+p.id+'" class="secondary">Editar</button><button data-action="delete" data-id="'+p.id+'" class="danger">Eliminar</button>':'')+
 (me.role!=='AUDITOR'?'<button data-action="stock" data-id="'+p.id+'" class="secondary">Stock</button>':'Solo lectura')+'</td></tr>').join('');
}
bind('loginTab','click',()=>authTab(false));bind('registerTab','click',()=>authTab(true));bind('cancelMfa','click',()=>authTab(false));
bind('loginForm','submit',async()=>{await accept(await api('/auth/login','POST',data($('loginForm'))));$('loginForm').reset();});
bind('registerForm','submit',async()=>{await api('/auth/register','POST',data($('registerForm')));$('registerForm').reset();authTab();notice('Cuenta creada como EMPLEADO. Ya puedes ingresar.');});
bind('mfaForm','submit',async()=>accept(await api('/auth/mfa/verify','POST',{challengeToken:challenge,code:data($('mfaForm')).code})));
bind('logout','click',()=>{clearSession();$('loginForm').reset();$('mfaForm').reset();notice('Sesión cerrada.');});
bind('refresh','click',async()=>{await dashboard();$('report').hidden=true;$('users').hidden=true;notice('Inventario actualizado.');});
bind('search','input',renderProducts);
bind('cancelEdit','click',resetProduct);
bind('productForm','submit',async()=>{
 const r=data($('productForm'));const id=r.id;delete r.id;r.price=Number(r.price);r.stock=Number(r.stock);
 await api('/products'+(id?'/'+id:''),id?'PUT':'POST',r);resetProduct();await loadProducts();notice('Producto guardado.');
});
bind('productRows','click',async e=>{
 const b=e.target.closest('button');if(!b)return;const p=products.find(p=>p.id===Number(b.dataset.id));if(!p)return;
 if(b.dataset.action==='edit'){for(const key of ['id','name','price','stock','store'])$('productForm').elements[key].value=p[key];$('productFormTitle').textContent='Editar producto';$('productForm').scrollIntoView({behavior:'smooth'});}
 if(b.dataset.action==='delete'&&confirm('¿Eliminar '+p.name+'?')){await api('/products/'+p.id,'DELETE');await loadProducts();notice('Producto eliminado.');}
 if(b.dataset.action==='stock'){const value=prompt('Nuevo stock para '+p.name,p.stock);if(value===null)return;
 if(!/^\d+$/.test(value)||!Number.isSafeInteger(Number(value)))throw Error('Ingresa un entero mayor o igual a cero.');
 await api('/products/'+p.id+'/stock','PATCH',{stock:Number(value)});await loadProducts();notice('Stock actualizado.');}
});
bind('setupMfa','click',async()=>{const r=await api('/auth/mfa/setup','POST',{});$('secret').textContent=r.secret;$('otpLink').href=r.otpauthUrl;$('mfaSetup').hidden=false;});
bind('enableMfaForm','submit',async()=>{await accept(await api('/auth/mfa/enable','POST',data($('enableMfaForm'))));$('enableMfaForm').reset();notice('MFA activado. En tu próximo ingreso se solicitará el código.');});
bind('reportButton','click',async()=>{reportData=await api('/reports/inventory');$('report').hidden=false;$('reportSummary').textContent=reportData.scope+' · '+reportData.productCount+' productos · '+reportData.totalStock+' unidades · '+money(reportData.inventoryValue);$('report').scrollIntoView({behavior:'smooth'});});
bind('downloadReport','click',()=>{if(!reportData)throw Error('Genera un reporte antes de descargarlo.');const url=URL.createObjectURL(new Blob([JSON.stringify(reportData,null,2)],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download='techstore-inventario.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
async function loadUsers(){
 const users=await api('/users');$('users').hidden=false;
 $('userRows').innerHTML=users.map(u=>'<tr data-id="'+u.id+'"><td>'+(me.role==='ADMIN'?'<input aria-label="Nombre de '+escape(u.email)+'" data-full-name value="'+escape(u.fullName)+'" required maxlength="150">':'<strong>'+escape(u.fullName)+'</strong>')+'<small>'+escape(u.email)+'</small></td><td>'+
 (me.role==='ADMIN'?'<input aria-label="Tienda de '+escape(u.email)+'" data-store value="'+escape(u.store)+'" required maxlength="100">':escape(u.store))+
 '</td><td>'+(me.role==='ADMIN'?'<select aria-label="Rol de '+escape(u.email)+'" data-role>'+['ADMIN','GERENTE','EMPLEADO','AUDITOR'].map(r=>'<option '+(r===u.role?'selected':'')+'>'+r+'</option>').join('')+'</select>':escape(u.role))+
 '</td><td>'+(u.accountLocked?'Bloqueado':'Activo')+(u.mfaEnabled?' · MFA':'')+'</td><td>'+
 (me.role==='ADMIN'?'<button type="button" data-action="save" class="secondary">Guardar</button>'+(u.accountLocked?'<button type="button" data-action="unlock">Desbloquear</button>':''):'Solo lectura')+'</td></tr>').join('');
}
bind('usersButton','click',async()=>{await loadUsers();$('users').scrollIntoView({behavior:'smooth'});});
bind('userRows','click',async e=>{
 const b=e.target.closest('button');if(!b)return;const row=b.closest('tr');const id=row.dataset.id;
 if(b.dataset.action==='save'){const store=row.querySelector('[data-store]').value.trim();const fullName=row.querySelector('[data-full-name]').value.trim();if(!store||!fullName)throw Error('Nombre y tienda son obligatorios.');
 await api('/users/'+id,'PUT',{role:row.querySelector('[data-role]').value,store,fullName});}
 else await api('/users/'+id+'/unlock','POST',{});
 notice('Usuario actualizado.');if(Number(id)===me.id){clearSession();notice('Tu sesión fue revocada por el cambio. Ingresa nuevamente.');}else await loadUsers();
});
(async()=>{
 try{
  const providers=await api('/auth/providers');for(const p of ['google','github'])$(p).hidden=!providers[p];$('socialHint').hidden=providers.google||providers.github;
  const code=new URLSearchParams(location.hash.slice(1)).get('oauthCode');
  if(code){history.replaceState(null,'','/');await accept(await api('/auth/oauth2/exchange','POST',{code}));}
  else if(token)await dashboard();
  if(new URLSearchParams(location.search).has('oauthError'))notice('No se pudo completar el acceso social. Revisa las credenciales, el email verificado o si ya usas otro proveedor.',true);
 }catch(err){clearSession();notice(err.message,true);}
})();
