# TechStore — Sistema de Gestión de Inventario

Laboratorio basado en el proyecto existente: Spring Boot 4.0.8, Java 17+, Maven, Spring Security, JPA y H2. El frontend HTML/CSS/JavaScript se sirve desde la misma aplicación y no necesita Node ni instalación independiente.

## Ejecutar

Desde PowerShell, entra en la carpeta que contiene `pom.xml` (la carpeta `inventory` dentro del directorio del workspace):

```powershell
cd C:\Users\Tecsup\Downloads\inventory\inventory
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Abre **http://localhost:8081/**. En Linux/macOS usa `./mvnw` en lugar de `.\mvnw.cmd`. El wrapper necesita acceso a Maven Central la primera vez o una caché completa de dependencias y plugins.

El perfil `demo` crea estas cuentas nuevas asignadas a Lima Centro. Conserva también los productos de demostración anteriores de Central y Norte:

| Email | Contraseña | Rol | Tienda |
| --- | --- | --- | --- |
| admin@techstore.test | TechStore1! | ADMIN | Lima Centro |
| gerente@techstore.test | TechStore1! | GERENTE | Lima Centro |
| empleado@techstore.test | TechStore1! | EMPLEADO | Lima Centro |
| auditor@techstore.test | TechStore1! | AUDITOR | Lima Centro |

Estas cuentas son exclusivamente para demostrar el laboratorio. El administrador `admin@techstore.test` con contraseña `TechStore1!` también se crea al iniciar sin el perfil `demo`, mediante la configuración bootstrap local. Las demás cuentas requieren el perfil `demo`; los cinco productos de Lima Centro se cargan automáticamente por defecto. Una cuenta existente conserva su contraseña. Puedes reemplazar las credenciales iniciales con `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD`.

### Inventario después del login

Tras completar el login y, si está activo, MFA, se abre el panel de inventario con nombre, precio en soles, stock, tienda y acciones según el rol.

| Producto | Precio | Stock | Tienda |
| --- | --- | --- | --- |
| Laptop Lenovo | S/ 2500.00 | 10 | Lima Centro |
| Mouse Logitech | S/ 80.00 | 25 | Lima Centro |
| Teclado Redragon | S/ 150.00 | 15 | Lima Centro |
| Monitor Samsung | S/ 850.00 | 8 | Lima Centro |
| Audífonos HyperX | S/ 250.00 | 12 | Lima Centro |

La carga comprueba nombre y tienda: no duplica productos ni sobrescribe precios o stocks existentes. Si eliminas un producto de demostración, se volverá a crear en el siguiente inicio mientras la carga siga habilitada. Para deshabilitarla establece `DEMO_INVENTORY_ENABLED=false`.

Las cuentas existentes conservan su tienda. Si tu GERENTE o EMPLEADO tiene otra tienda, ADMIN debe asignarle **Lima Centro** desde Usuarios y después debes iniciar sesión nuevamente. El registro sugiere Lima Centro y las cuentas sociales nuevas se asignan a esa tienda. ADMIN y AUDITOR pueden consultar todas las tiendas. No se amplían los permisos para mostrar productos de tiendas ajenas.

En IntelliJ puedes ejecutar `InventoryApplication.main()` con el argumento `--spring.profiles.active=demo`. Inicia sesión con las cuentas de la tabla anterior en **http://localhost:8081/** para ver el inventario.

Para crear un administrador inicial sin el perfil demo:

```powershell
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@mitienda.com"
$env:BOOTSTRAP_ADMIN_PASSWORD = "MiClaveSegura1!"
.\mvnw.cmd spring-boot:run
```

La creación inicial no cambia una cuenta que ya exista con ese email. El registro público siempre asigna EMPLEADO; el administrador cambia roles y tiendas desde la opción **Usuarios** o mediante la API.

Por defecto H2 funciona en memoria: los datos desaparecen al detener la aplicación. Para conservarlos entre ejecuciones:

```powershell
$env:DB_URL = "jdbc:h2:file:./data/techstore"
```

## Funcionalidades y seguridad

- Registro con email único normalizado, nombre completo y tienda obligatorios. Contraseña de mínimo 8 caracteres con mayúscula, número y carácter especial, almacenada con BCrypt.
- Login con JWT y bloqueo de cuenta al quinto error de contraseña. Un login correcto reinicia los errores de contraseña.
- MFA TOTP compatible con Google Authenticator: seis dígitos, período de 30 segundos, configuración y activación mediante un código válido.
- Con MFA activo, el primer paso entrega únicamente `mfaRequired`, `challengeToken` y `expiresIn: 300`; no entrega el JWT definitivo. El desafío no sirve como token Bearer.
- Tres errores MFA bloquean la cuenta, incluso si se crean nuevos desafíos de login. Un código correcto reinicia el contador MFA. Un administrador puede desbloquear la cuenta.
- El verificador acepta una ventana de desfase de un período de 30 segundos hacia cada lado. Mantén sincronizados el reloj del teléfono y el servidor.
- Los desafíos duran cinco minutos, se consumen tras su uso y distinguen los propósitos MFA y OAuth2.
- JWT firmado, vencimiento por defecto de una hora y verificación del usuario en cada petición. Bloquear, desbloquear, cambiar rol/tienda o activar MFA revoca los JWT anteriores mediante `tokenVersion`. Completar MFA también invalida otros desafíos y JWT anteriores del usuario.
- La API usa `Authorization: Bearer <token>` y no autentica mediante cookies de sesión. Solo el intercambio OAuth2 usa una sesión temporal, que se invalida al completar el acceso.
- `/api/me` y la lista de usuarios omiten contraseñas y secretos MFA. La configuración y activación de MFA requieren JWT.
- Inventario con nombre, precio decimal positivo, stock entero no negativo, tienda y control de concurrencia optimista.
- Frontend con login, registro, MFA, búsqueda, inventario, acciones según rol, usuarios y descarga de reportes JSON.
- ADMIN puede editar nombre, tienda y rol desde Usuarios. El frontend limpia la sesión y los datos visibles al cerrar sesión, al bloquearse la cuenta o al detectar un JWT inválido en los endpoints de datos.

La autorización se aplica en Spring Security y en el servicio de inventario; ocultar botones en el frontend no sustituye esos controles.

| Operación | ADMIN | GERENTE | EMPLEADO | AUDITOR |
| --- | --- | --- | --- | --- |
| Consultar productos | Todas las tiendas | Su tienda | Su tienda | Todas las tiendas |
| Crear, editar precios y eliminar productos | Todas las tiendas | Su tienda | No | No |
| Actualizar stock | Todas las tiendas | Su tienda | Su tienda | No |
| Consultar/generar reportes | Todas las tiendas | Su tienda | No | Todas las tiendas |
| Consultar usuarios | Sí | No | No | Sí, sin secretos |
| Cambiar roles, tiendas, nombres y desbloquear usuarios | Sí | No | No | No |

Un gerente tampoco puede trasladar un producto a otra tienda mediante `PUT`. Las tiendas se comparan por su nombre exacto, por ejemplo `Central` y `Norte`. Todos los perfiles pueden configurar su propio MFA; el auditor no puede modificar inventario ni usuarios.

## Variables de entorno

| Variable | Uso / valor por defecto |
| --- | --- |
| `PORT` | Puerto HTTP; `8081` |
| `JWT_SECRET` | Clave de firma de al menos 32 bytes; existe un valor local para el laboratorio |
| `JWT_EXPIRATION_MS` | Duración JWT; `3600000` |
| `DB_URL` | `jdbc:h2:mem:techstore` |
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales H2; `sa` y contraseña vacía |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Creación inicial opcional del administrador |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Credenciales reales de Google |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | Credenciales reales de GitHub |
| `OAUTH_BASE_URL` | URL base de callbacks; `http://localhost:8081` |
| `H2_CONSOLE_ENABLED` | Consola H2; `false` por defecto, habilitada por el perfil demo |
| `DEMO_INVENTORY_ENABLED` | Carga de los cinco productos de Lima Centro; `true` |

Usa una clave JWT propia al salir del laboratorio. Las variables se leen del entorno; no hay carga automática de archivos `.env`.

## OAuth2: Google y GitHub

Cada proveedor se habilita solamente cuando están presentes tanto su client ID como su client secret. Sin credenciales, la aplicación permite login local y el frontend informa que el acceso social no está disponible. No se incluyen credenciales reales ni se requieren para iniciar el laboratorio.

### Google

En Google Cloud Console configura la pantalla de consentimiento y crea un cliente OAuth de tipo aplicación web. Registra:

- Origen local: `http://localhost:8081`.
- URI de redirección autorizada: `http://localhost:8081/login/oauth2/code/google`.
- Si la aplicación está en modo de prueba, agrega la cuenta que usarás como usuario de prueba.

Configura `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` con los valores de ese cliente. Se solicitan los scopes OpenID Connect del proveedor y se exige un email verificado.

### GitHub

En GitHub → Settings → Developer settings → OAuth Apps crea una aplicación con:

- Homepage URL: `http://localhost:8081`.
- Authorization callback URL: `http://localhost:8081/login/oauth2/code/github`.

Configura `GITHUB_CLIENT_ID` y `GITHUB_CLIENT_SECRET`. Se solicitan `read:user` y `user:email`; el servidor consulta los emails de GitHub y usa uno verificado, dando preferencia al principal.

Define estas variables en PowerShell antes de arrancar y luego reinicia la aplicación. Si cambias de puerto o dominio, actualiza `OAUTH_BASE_URL` y los callbacks registrados en ambos proveedores. El flujo externo necesita conexión a los proveedores.

El login social crea una cuenta EMPLEADO asignada inicialmente a Lima Centro. El administrador puede cambiar su tienda y rol. Nunca se vincula automáticamente una identidad social a una cuenta existente solo por coincidir el email: usa el método original si ese email ya está registrado.

Tras autenticar con el proveedor, el navegador recibe un código temporal en el fragmento de la URL. El frontend lo intercambia en `/api/auth/oauth2/exchange`; si la cuenta tiene MFA activo, debe completar TOTP antes de obtener JWT. La sesión OAuth2 no permite acceder directamente a `/api/me`.

## Endpoints principales

Los endpoints protegidos requieren `Authorization: Bearer <JWT>`.

| Método | Ruta | Acceso / uso |
| --- | --- | --- |
| POST | `/api/auth/register` | Público; email, password, fullName, store |
| POST | `/api/auth/login` | Público; email y password |
| GET | `/api/me` | Usuario autenticado |
| POST | `/api/auth/mfa/setup` | Autenticado; devuelve secret y otpauthUrl |
| POST | `/api/auth/mfa/enable` | Autenticado; `{ "code": "123456" }` |
| POST | `/api/auth/mfa/verify` | `{ "challengeToken": "...", "code": "123456" }` |
| GET | `/api/auth/providers` | Público; proveedores habilitados |
| GET | `/oauth2/authorization/google` | Inicio de login Google si está configurado |
| GET | `/oauth2/authorization/github` | Inicio de login GitHub si está configurado |
| POST | `/api/auth/oauth2/exchange` | Público; `{ "code": "..." }`, código temporal de un uso |
| GET | `/api/products` | Productos visibles según rol y tienda |
| GET | `/api/products/{id}` | Consulta individual con restricción de tienda |
| POST | `/api/products` | ADMIN o GERENTE; crea y responde 201 |
| PUT | `/api/products/{id}` | ADMIN o GERENTE; actualiza producto completo |
| PATCH | `/api/products/{id}/stock` | ADMIN, GERENTE o EMPLEADO; `{ "stock": 20 }` |
| DELETE | `/api/products/{id}` | ADMIN o GERENTE; responde 204 |
| GET | `/api/reports/inventory` | ADMIN, GERENTE o AUDITOR; datos y totales |
| GET | `/api/users` | ADMIN o AUDITOR; lista sin secretos |
| PUT | `/api/users/{id}` | ADMIN; role, store y fullName |
| POST | `/api/users/{id}/unlock` | ADMIN; reinicia bloqueo y contadores |

Ejemplo de producto para POST o PUT:

```json
{"name":"Teclado","price":89.90,"stock":10,"store":"Central"}
```

Respuestas relevantes: `400` validación, `401` autenticación inválida o token ausente, `403` permiso/tienda incorrectos, `404` recurso inexistente, `409` conflicto de datos o actualización concurrente, `423` cuenta bloqueada.

## Demostración manual

Ejecuta con el perfil demo y usa sesiones separadas o cierra sesión al cambiar de rol. Para verificar los permisos reales utiliza Postman o la pestaña Network del navegador además de observar los botones.

1. **Registro:** crea una cuenta con todos los campos y contraseña válida. Repite el email cambiando mayúsculas: debe rechazarse. Prueba contraseña corta, sin mayúscula, sin número o sin símbolo; prueba nombre/tienda vacíos: debe responder 400. La nueva cuenta debe tener rol EMPLEADO.
2. **JWT:** inicia sesión y revisa `/api/me` con el token obtenido: 200. Sin token, con uno alterado o usando un `challengeToken` como Bearer: 401. El perfil no debe devolver password ni mfaSecret.
3. **Bloqueo de login:** usa una cuenta de prueba y envía cinco contraseñas incorrectas. Primeros cuatro errores: 401; quinto: 423. La contraseña correcta después del bloqueo también debe dar 423. Con ADMIN, desbloquea desde Usuarios y comprueba que puede volver a ingresar.
4. **Configurar MFA:** entra en una cuenta de prueba, pulsa Configurar MFA y agrega la clave manualmente en Google Authenticator como TOTP. Ingresa el código actual de seis dígitos para activar. El código de la aplicación cambia cada 30 segundos. La activación reemplaza el JWT y revoca el anterior.
5. **Login MFA:** cierra sesión y vuelve a ingresar. Revisa la respuesta del primer paso: mfaRequired=true y challengeToken, sin token definitivo. Ingresa el código correcto: se entrega JWT y aparece el inventario. Reutilizar el desafío o verificar solo con email/código debe dar 401. Esperar más de cinco minutos vuelve inválido el desafío.
6. **Bloqueo MFA:** en una cuenta con MFA, envía tres códigos incorrectos no vacíos. Primeros dos errores: 401; tercero: 423. Volver a hacer login entre errores no reinicia este contador. ADMIN puede desbloquearla. Prueba también setup y enable sin JWT: 401.
7. **ADMIN:** consulta Central y Norte, crea/edita/elimina productos de ambas tiendas. En Usuarios cambia el rol y tienda de una cuenta; su JWT anterior debe dejar de servir. Consulta y descarga el reporte de todas las tiendas.
8. **GERENTE:** las cuentas demo nuevas deben ver Lima Centro. Crea y modifica un producto en Lima Centro y genera su reporte. Usando el ID de un producto Norte obtenido como ADMIN, intenta leerlo, editarlo, eliminarlo o trasladar un producto Lima Centro a Norte: 403.
9. **EMPLEADO:** consulta Lima Centro y cambia stock: 200. Intenta cambiar precio con PUT, crear o eliminar: 403. Stock negativo: 400. Cambiar stock de Norte: 403. Reportes y usuarios: 403.
10. **AUDITOR:** consulta productos de Central y Norte y la lista de usuarios sin secretos. Genera y descarga el reporte global. Crear, editar, eliminar, cambiar stock, asignar roles o desbloquear: 403.
11. **OAuth2 sin credenciales:** `/api/auth/providers` muestra false y no aparecen enlaces activos a los proveedores. El login local sigue disponible.
12. **OAuth2 con credenciales reales:** configura cada proveedor, reinicia y prueba sus botones con emails verificados que no pertenezcan a otra cuenta. Debe crearse una cuenta EMPLEADO. Activa MFA en esa cuenta, cierra sesión y repite login social: debe pedir TOTP antes del JWT. Reutilizar el código de intercambio debe dar 401.
13. **Frontend:** revisa búsqueda, edición, limpiar formulario, stock, mensajes de error, cierre de sesión, botones por rol y descarga JSON. Después de editar y limpiar, guardar otro producto debe crear uno nuevo. Como ADMIN, modifica también el nombre de un usuario. Comprueba la vista en una ventana estrecha. Los botones ocultos deben coincidir con las restricciones de la API. Un JWT revocado al consultar datos debe devolver al login y limpiar la información anterior.

Ejemplo mínimo de API en PowerShell (ejecutar con la aplicación iniciada):

```powershell
$base = "http://localhost:8081"
$body = @{email="admin@techstore.test"; password="TechStore1!"} | ConvertTo-Json
$login = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType "application/json" -Body $body
# Este ejemplo requiere que la cuenta todavía no tenga MFA activo.
$headers = @{Authorization="Bearer $($login.token)"}
Invoke-RestMethod "$base/api/me" -Headers $headers
Invoke-RestMethod "$base/api/products" -Headers $headers
Invoke-RestMethod "$base/api/reports/inventory" -Headers $headers
```

## Pruebas automatizadas disponibles

Para ejecutar en tu entorno con Maven y las dependencias disponibles:

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

- `InventoryApplicationTests`: carga del contexto.
- `SecurityInventoryTests`: registro, BCrypt, JWT, bloqueos, configuración/activación/login MFA, expiración de desafíos, roles, tiendas, stock, reportes, administración, frontend y código OAuth2 de un uso.
- `OAuthConfigurationTests`: configuración de ambos proveedores, scopes, callbacks, redirección inicial y aislamiento de la sesión OAuth2 respecto a la API. Los valores test-client/test-secret son datos de prueba locales; no representan credenciales externas y no hacen un login real.

Las pruebas usan H2 en memoria independiente de la base de datos de la aplicación. El acceso real a Google/GitHub se demuestra manualmente con credenciales propias.

**Validación realizada sobre el código actual:** compilación local con el compilador del JDK y destino Java 17; **25 pruebas JUnit Jupiter ejecutadas, 0 fallos**, incluida la prueba de contexto original. Las pruebas comprueban MFA con códigos incorrectos de seis dígitos, bloqueo tras tres errores, JWT después del segundo factor, roles, tiendas, inventario y OAuth2. La nueva prueba verifica los cinco productos de Lima Centro, la carga sin duplicados y que reiniciar la carga conserva el stock modificado. Los callbacks Google simulados comprueban creación de EMPLEADO, rechazo de email no verificado, protección de cuentas locales contra vinculación automática, bloqueo y MFA previo al JWT.

Se comprobó el servidor demo actualizado por HTTP en el puerto temporal 8082: frontend 200 y login/JWT de los cuatro roles. ADMIN y AUDITOR ven ocho productos, incluidos los tres anteriores y los cinco nuevos; GERENTE y EMPLEADO ven los cinco de Lima Centro. El servidor temporal se detuvo al terminar. En la etapa anterior se verificaron además el 401 sin JWT y el inicio sin credenciales OAuth2. El JavaScript pasó la revisión de sintaxis y de referencias HTML en esa etapa; en este cambio se conserva. Los clics y la presentación visual en un navegador siguen requiriendo comprobación manual.

Se ejecutó Maven `verify`, pero se detuvo antes de compilar al intentar obtener `org.springframework.boot:spring-boot-starter-actuator:4.0.8`: Maven Central devolvió `Permission denied: getsockopt`. Se conservaron todas las dependencias del `pom.xml`, incluido Actuator. Por ello **la compilación completa con Maven y el empaquetado del JAR siguen pendientes** en un entorno con acceso a las dependencias. La comprobación local usa las bibliotecas disponibles en la caché y no incluye Actuator, que no está disponible allí. El login externo real también requiere tus credenciales de Google/GitHub.

Para repetir la comprobación local con las bibliotecas disponibles:

```powershell
.\tools\verify-local.ps1 -Jdk "C:\Program Files\Java\jdk-24"
# Opcional: -Repository "C:\ruta\al\repositorio-maven-local"
```

El script usa el compilador estándar de Java con --release 17 y -parameters, y ejecuta las mismas pruebas con el motor JUnit Jupiter. No sustituye el ciclo de Maven ni empaqueta el JAR. Resultados: target/local-test-reports/summary.txt; registro de esta ejecución: target/inventory-validation.log. Las bibliotecas se buscan en .build-cache/repository o en .m2/repository.

## Archivos de la implementación

- Autenticación/MFA: `AuthController.java`, `AuthService.java`, `MfaService.java`, `MfaVerifyRequest.java`, `AuthChallenge.java`, `AuthChallengeRepository.java`.
- Seguridad/JWT: `SecurityConfig.java`, `JwtService.java`, `JwtAuthFilter.java`, `User.java`, `UserRepository.java`, `MeController.java`, `GlobalExceptionHandler.java`. Se conserva el enum `Role.java` existente con los cuatro perfiles.
- Inventario: `Product.java`, `ProductRequest.java`, `ProductRepository.java`, `InventoryService.java`, `ProductController.java`.
- Administración y datos de demostración: `UserController.java`, `BootstrapConfig.java`.
- OAuth2: `OAuthConfig.java`, `OAuthLoginSuccessHandler.java`.
- Recursos: `application.properties`, `application-demo.properties`, `static/index.html`, `static/style.css`, `static/app.js`.
- Pruebas ejecutadas (25 correctas): `InventoryApplicationTests.java`, `SecurityInventoryTests.java`, `OAuthConfigurationTests.java`.
- Entrega: `README.md`, `.gitignore`.

Últimos ajustes de código: período TOTP explícito, constantes para los límites de intentos/desafíos, rechazo de desafíos vacíos, limpieza de sesión en frontend y edición de nombres por ADMIN.

## Revisión de botones y formularios

Se revisaron por lectura sus identificadores HTML, eventos JavaScript, rutas REST y permisos. Esta revisión no sustituye una ejecución en navegador.

| Control | Acción implementada |
| --- | --- |
| Ingresar / Crear cuenta (pestañas) | Cambiar el formulario visible |
| Ingresar / Registrarme | Login y registro mediante la API |
| Verificar e ingresar / Volver | Verificar el desafío MFA o volver al login |
| Google / GitHub | Iniciar OAuth2; visibles solo si hay credenciales configuradas |
| Cerrar sesión | Eliminar JWT local y limpiar formularios, datos y secretos visibles |
| Actualizar / Buscar | Recargar inventario y filtrar productos visibles |
| Configurar MFA / Activar MFA | Obtener clave TOTP y confirmar activación con código |
| Abrir autenticador | Abrir URI otpauth en una aplicación compatible; alternativa: ingresar clave manualmente |
| Guardar producto / Limpiar | Crear o actualizar según ID; limpiar vuelve al modo de creación |
| Editar / Eliminar / Stock | Editar producto, confirmar eliminación o actualizar stock según rol |
| Generar reporte / Descargar JSON | Obtener reporte autorizado y descargarlo |
| Usuarios / Guardar / Desbloquear | Consultar usuarios y realizar acciones administrativas según rol |

Los controles que envían solicitudes deshabilitan el botón durante la operación y evitan repetir la misma acción mientras está pendiente. Recargar/modificar inventario descarta el reporte anterior: genera uno nuevo para descargar datos actuales. Las respuestas pendientes de una sesión anterior no se aplican después de cerrar sesión o cambiar de JWT. Cambiar el propio perfil administrativo devuelve al login porque revoca su JWT.

Pendiente manual: recorrer estos controles con los cuatro perfiles, comprobar mensajes de error, evitar solicitudes duplicadas con doble clic y confirmar los flujos reales de Authenticator y Google/GitHub.
