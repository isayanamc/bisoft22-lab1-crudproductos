# verificar-entorno.ps1
# Revisa que los pasos 1-6 del Lab 1 esten funcionando: Docker, MariaDB, Keycloak sobre MariaDB,
# realm "inventario", login de los dos usuarios, roles en el token y passwords hasheados.
# Si la app de Spring esta corriendo en :8080, tambien prueba el login de la API y el 401.
#
# Uso (desde la raiz del repo, con "docker compose up -d" ya corriendo en la carpeta docker):
#   powershell -ExecutionPolicy Bypass -File .\verificar-entorno.ps1
# Solo lee: no crea, borra ni cambia nada.

$ErrorActionPreference = 'Continue'
$script:ok = 0
$script:fallos = 0

$Keycloak  = 'http://localhost:8180'
$Realm     = 'inventario'
$ClientId  = 'inventario-api'
$Api       = 'http://localhost:8080'
$Mariadb   = 'lab1_mariadb'
$Usuarios  = @(
    @{ user = 'admin';   pass = 'admin123';   rol = 'SUPER-ADMIN-ROLE' },
    @{ user = 'usuario'; pass = 'usuario123'; rol = 'USER' }
)

function Paso($texto)  { Write-Host ''; Write-Host "== $texto" -ForegroundColor Cyan }
function Bien($texto)  { $script:ok++;     Write-Host "  [OK]    $texto" -ForegroundColor Green }
function Mal($texto, $pista) {
    $script:fallos++
    Write-Host "  [FALLA] $texto" -ForegroundColor Red
    if ($pista) { Write-Host "          -> $pista" -ForegroundColor Yellow }
}
function Info($texto)  { Write-Host "          $texto" -ForegroundColor Gray }

# Cualquier error inesperado del propio script cuenta como falla (nunca "0 fallas" si algo exploto)
trap { Mal "Error inesperado en el script: $($_.Exception.Message)" 'Reemplaza verificar-entorno.ps1 completo, no por partes'; continue }

# Corre un query en el contenedor de MariaDB como root y devuelve las filas (separadas por tab)
function Sql($query) {
    $salida = docker exec $Mariadb mariadb -uroot -proot -N -B -e $query 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($salida -join ' ') }
    return @($salida | Where-Object { $_ -and ($_ -notmatch 'Warning') })
}

# Decodifica el payload (parte del medio) de un JWT
function Leer-Jwt($jwt) {
    $p = $jwt.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($p.Length % 4) { 2 { $p += '==' } 3 { $p += '=' } }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
}

# Devuelve el codigo HTTP de una llamada, sin explotar con 4xx/5xx
function Codigo-Http($metodo, $url, $headers = @{}, $body = $null) {
    try {
        $params = @{ Method = $metodo; Uri = $url; Headers = $headers; UseBasicParsing = $true }
        if ($body) { $params.Body = $body; $params.ContentType = 'application/json' }
        return (Invoke-WebRequest @params).StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

# Simula el preflight (OPTIONS) que manda el navegador antes de una llamada de otro origen.
# Devuelve el codigo HTTP y el header Access-Control-Allow-Origin de la respuesta.
function Preflight($url, $origen) {
    $headers = @{
        'Origin'                         = $origen
        'Access-Control-Request-Method'  = 'POST'
        'Access-Control-Request-Headers' = 'authorization,content-type'
    }
    try {
        $r = Invoke-WebRequest -Method Options -Uri $url -Headers $headers -UseBasicParsing
        return @{ codigo = [int]$r.StatusCode; permitido = "$($r.Headers['Access-Control-Allow-Origin'])" }
    } catch {
        $codigo = 0
        if ($_.Exception.Response) { $codigo = [int]$_.Exception.Response.StatusCode }
        return @{ codigo = $codigo; permitido = '' }
    }
}

# Llama a la API con un token y devuelve el codigo HTTP y el JSON de la respuesta (si hay)
function Llamar-Api($metodo, $url, $token, $body = $null) {
    $params = @{ Method = $metodo; Uri = $url; UseBasicParsing = $true; Headers = @{ Authorization = "Bearer $token" } }
    if ($body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json)); $params.ContentType = 'application/json; charset=utf-8' }
    try {
        $r = Invoke-WebRequest @params
        $json = $null
        if ($r.Content) { $json = $r.Content | ConvertFrom-Json }
        return @{ codigo = [int]$r.StatusCode; json = $json }
    } catch {
        $codigo = 0
        if ($_.Exception.Response) { $codigo = [int]$_.Exception.Response.StatusCode }
        return @{ codigo = $codigo; json = $null }
    }
}

function Esperar($descripcion, $obtenido, $esperado) {
    if ($obtenido -eq $esperado) { Bien "$descripcion -> $obtenido" }
    else { Mal "$descripcion -> $obtenido (se esperaba $esperado)" }
}

# Requisitos 9 y 10 con tokens reales de Keycloak. Crea una categoria de prueba y la borra al final.
function Probar-Roles-Categoria {
    $url = "$Api/api/v1/categorias"
    $adm = $tokens['admin']
    $usr = $tokens['usuario']
    $nombre = "Verificacion-$(Get-Date -Format 'yyyyMMddHHmmss')"

    Paso 'Roles en Categoria con tokens reales (requisitos 9 y 10)'

    Esperar 'USER lista categorias' (Llamar-Api 'Get' $url $usr).codigo 200
    Esperar 'USER intenta crear' (Llamar-Api 'Post' $url $usr @{ nombre = $nombre }).codigo 403

    $creada = Llamar-Api 'Post' $url $adm @{ nombre = $nombre; descripcion = 'Creada por verificar-entorno.ps1' }
    Esperar 'SUPER-ADMIN crea' $creada.codigo 201
    if ($creada.codigo -ne 201 -or -not $creada.json) { Info 'Sin categoria creada no se pueden probar editar y borrar.'; return }
    $id = $creada.json.id

    Esperar 'USER consulta por id' (Llamar-Api 'Get' "$url/$id" $usr).codigo 200
    Esperar 'USER intenta editar' (Llamar-Api 'Put' "$url/$id" $usr @{ nombre = "$nombre-x" }).codigo 403
    Esperar 'USER intenta borrar' (Llamar-Api 'Delete' "$url/$id" $usr).codigo 403
    Esperar 'SUPER-ADMIN edita' (Llamar-Api 'Put' "$url/$id" $adm @{ nombre = "$nombre-editada"; descripcion = 'Editada' }).codigo 200
    Esperar 'SUPER-ADMIN crea con nombre repetido' (Llamar-Api 'Post' $url $adm @{ nombre = "$nombre-editada" }).codigo 409
    Esperar 'SUPER-ADMIN crea sin nombre' (Llamar-Api 'Post' $url $adm @{ descripcion = 'Sin nombre' }).codigo 400
    Esperar 'SUPER-ADMIN borra' (Llamar-Api 'Delete' "$url/$id" $adm).codigo 204
    Esperar 'La categoria borrada ya no existe' (Llamar-Api 'Get' "$url/$id" $adm).codigo 404
}

# Requisitos 4, 5, 9, 10 y la regla de negocio (409) con tokens reales.
# Crea una categoria y un producto de prueba y borra los dos al final.
function Probar-Productos {
    $url = "$Api/api/v1/productos"
    $adm = $tokens['admin']
    $usr = $tokens['usuario']
    $sufijo = Get-Date -Format 'yyyyMMddHHmmss'

    Paso 'Productos y regla de negocio con tokens reales (requisitos 4, 5, 9 y 10)'
    $lista = Llamar-Api 'Get' $url $usr
    if ($lista.codigo -eq 404) { Info 'ProductoController aun no existe (paso 8); se omite esta parte.'; return }
    Esperar 'USER lista productos' $lista.codigo 200

    $cat = Llamar-Api 'Post' "$Api/api/v1/categorias" $adm @{ nombre = "Verificacion-Prod-$sufijo" }
    if ($cat.codigo -ne 201) { Mal "No se pudo crear la categoria de prueba (respondio $($cat.codigo))"; return }
    $catId = $cat.json.id

    Esperar 'USER intenta crear producto' (Llamar-Api 'Post' $url $usr @{ nombre = 'x'; precio = 1; stock = 1; categoriaId = $catId }).codigo 403

    $prod = Llamar-Api 'Post' $url $adm @{ nombre = "Verificacion-$sufijo"; descripcion = 'Creado por verificar-entorno.ps1'; precio = 1500.50; stock = 10; categoriaId = $catId }
    Esperar 'SUPER-ADMIN crea producto' $prod.codigo 201
    if ($prod.codigo -eq 201 -and $prod.json) {
        $prodId = $prod.json.id
        if ($prod.json.categoriaId -eq $catId) { Bien "El producto quedo asignado a su categoria ($($prod.json.categoriaNombre))" }
        else { Mal 'El producto no quedo asignado a la categoria enviada' }

        Esperar 'USER consulta el producto' (Llamar-Api 'Get' "$url/$prodId" $usr).codigo 200
        Esperar 'USER intenta editar producto' (Llamar-Api 'Put' "$url/$prodId" $usr @{ nombre = 'x'; precio = 1; stock = 1; categoriaId = $catId }).codigo 403
        Esperar 'USER intenta borrar producto' (Llamar-Api 'Delete' "$url/$prodId" $usr).codigo 403
        Esperar 'SUPER-ADMIN edita producto' (Llamar-Api 'Put' "$url/$prodId" $adm @{ nombre = "Verificacion-$sufijo-editado"; precio = 2000; stock = 5; categoriaId = $catId }).codigo 200
        Esperar 'Borrar categoria con productos se bloquea' (Llamar-Api 'Delete' "$Api/api/v1/categorias/$catId" $adm).codigo 409
        Esperar 'SUPER-ADMIN borra producto' (Llamar-Api 'Delete' "$url/$prodId" $adm).codigo 204
    }

    Esperar 'Producto con categoria inexistente' (Llamar-Api 'Post' $url $adm @{ nombre = 'x'; precio = 1; stock = 1; categoriaId = 999999999 }).codigo 400
    Esperar 'Producto con precio negativo' (Llamar-Api 'Post' $url $adm @{ nombre = 'x'; precio = -1; stock = 1; categoriaId = $catId }).codigo 400
    Esperar 'Ya sin productos, la categoria se puede borrar' (Llamar-Api 'Delete' "$Api/api/v1/categorias/$catId" $adm).codigo 204
}

# ---------------------------------------------------------------------------
Paso 'Docker y contenedores (pasos 3 y 4)'
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Mal 'Docker no responde' 'Abri Docker Desktop y espera a que diga "Engine running".'
    Write-Host ''; Write-Host 'Sin Docker no se puede revisar lo demas.' -ForegroundColor Red; exit 1
}
Bien 'Docker esta corriendo'

foreach ($c in @($Mariadb, 'keycloak')) {
    $estado = docker inspect -f '{{.State.Status}}' $c 2>$null
    if ($estado -eq 'running') { Bien "Contenedor $c corriendo" }
    else { Mal "Contenedor $c no esta corriendo (estado: $estado)" 'En la carpeta docker: docker compose up -d   y luego: docker compose logs keycloak' }
}

# ---------------------------------------------------------------------------
Paso 'MariaDB: bases de la app y de Keycloak (pasos 3 y 4)'
try {
    $bases = Sql 'SHOW DATABASES'
    foreach ($b in @('lab1', 'keycloak')) {
        if ($bases -contains $b) { Bien "Existe la base '$b'" }
        else {
            $pista = if ($b -eq 'keycloak') {
                'El script de docker/mariadb/init solo corre con el volumen vacio. Si el volumen ya existia: docker compose down -v (borra los datos) y docker compose up -d'
            } else { 'Revisa MARIADB_DATABASE en docker-compose.yml' }
            Mal "No existe la base '$b'" $pista
        }
    }
} catch {
    Mal 'No pude consultar MariaDB' $_.Exception.Message
}

# ---------------------------------------------------------------------------
Paso 'Keycloak guarda su informacion en MariaDB (requisitos 8 y 12)'
try {
    $realms = Sql 'SELECT NAME FROM keycloak.REALM'
    if ($realms -contains $Realm) { Bien "El realm '$Realm' esta guardado en la base keycloak de MariaDB" }
    else { Mal "El realm '$Realm' no esta en MariaDB (realms: $($realms -join ', '))" 'Revisa que realm-inventario.json se este montando y el log de Keycloak (docker compose logs keycloak)' }

    $qRoles = @'
SELECT u.USERNAME, r.NAME
FROM keycloak.USER_ENTITY u
JOIN keycloak.REALM re           ON re.ID = u.REALM_ID
JOIN keycloak.USER_ROLE_MAPPING m ON m.USER_ID = u.ID
JOIN keycloak.KEYCLOAK_ROLE r    ON r.ID = m.ROLE_ID
WHERE re.NAME = 'inventario' AND r.NAME IN ('SUPER-ADMIN-ROLE', 'USER')
ORDER BY u.USERNAME
'@
    $filas = Sql $qRoles
    foreach ($u in $Usuarios) {
        if ($filas -contains "$($u.user)`t$($u.rol)") { Bien "En la BD, '$($u.user)' tiene el rol $($u.rol)" }
        else { Mal "En la BD no aparece '$($u.user)' con el rol $($u.rol)" 'Revisa "users" y "realmRoles" en realm-inventario.json' }
    }

    $qHash = @'
SELECT u.USERNAME,
       JSON_VALUE(c.CREDENTIAL_DATA, '$.algorithm'),
       LEFT(JSON_VALUE(c.SECRET_DATA, '$.value'), 30)
FROM keycloak.CREDENTIAL c
JOIN keycloak.USER_ENTITY u ON u.ID = c.USER_ID
JOIN keycloak.REALM re      ON re.ID = u.REALM_ID
WHERE re.NAME = 'inventario' AND c.TYPE = 'password'
'@
    $hashes = Sql $qHash
    if ($hashes.Count -eq 0) { Mal 'No hay passwords guardados para el realm' 'Revisa "credentials" en realm-inventario.json' }
    foreach ($h in $hashes) {
        $partes = $h -split "`t"
        $usuario = $partes[0]
        $plano = ($Usuarios | Where-Object { $_.user -eq $usuario }).pass
        if ($plano -and $partes[2] -like "*$plano*") { Mal "El password de '$usuario' esta en texto plano" }
        else { Bien "Password de '$usuario' hasheado con $($partes[1]): $($partes[2])..." }
    }
} catch {
    Mal 'No pude leer las tablas de Keycloak en MariaDB' "Si Keycloak recien arranco, espera un minuto. Detalle: $($_.Exception.Message)"
}

# ---------------------------------------------------------------------------
Paso 'Keycloak responde y emite tokens (pasos 5 y 6)'
$tokens = @{}
try {
    $cfg = Invoke-RestMethod "$Keycloak/realms/$Realm/.well-known/openid-configuration"
    Bien "Realm publicado. issuer = $($cfg.issuer)"
    if ($cfg.issuer -ne "$Keycloak/realms/$Realm") { Mal 'El issuer no calza con el issuer-uri de application.yml' }
} catch {
    Mal "Keycloak no responde en $Keycloak/realms/$Realm" 'Keycloak tarda en arrancar (~30-60 s). Mira: docker compose logs -f keycloak'
}

foreach ($u in $Usuarios) {
    try {
        $form = @{ grant_type = 'password'; client_id = $ClientId; username = $u.user; password = $u.pass }
        $r = Invoke-RestMethod -Method Post -Uri "$Keycloak/realms/$Realm/protocol/openid-connect/token" -Body $form
        $tokens[$u.user] = $r.access_token
        $claims = Leer-Jwt $r.access_token
        $rolesRealm = @($claims.realm_access.roles)
        $permisos = @()
        if ($claims.resource_access -and $claims.resource_access.$ClientId) { $permisos = @($claims.resource_access.$ClientId.roles) }

        if ($rolesRealm -contains $u.rol) { Bien "Login de '$($u.user)' OK; el token trae el rol $($u.rol)" }
        else { Mal "El token de '$($u.user)' no trae el rol $($u.rol)" "Roles en el token: $($rolesRealm -join ', ')" }
        Info "realm_access.roles: $($rolesRealm -join ', ')"
        Info "permisos ($ClientId): $($permisos -join ', ')"
    } catch {
        Mal "Login de '$($u.user)' fallo contra Keycloak" $_.Exception.Message
    }
}

try {
    Invoke-RestMethod -Method Post -Uri "$Keycloak/realms/$Realm/protocol/openid-connect/token" `
        -Body @{ grant_type = 'password'; client_id = $ClientId; username = 'admin'; password = 'incorrecta' } | Out-Null
    Mal 'Keycloak acepto un password incorrecto'
} catch {
    Bien 'Keycloak rechaza un password incorrecto'
}

# ---------------------------------------------------------------------------
Paso "API de Spring en $Api (solo si la app esta corriendo)"
$salud = Codigo-Http 'Get' "$Api/actuator/health"
if ($salud -eq 0) {
    Info 'La app no esta corriendo; se omite esta parte. Levantala desde IntelliJ y volve a correr el script.'
} else {
    if ($salud -eq 200) { Bien '/actuator/health responde 200 (la app arranco y se conecto a MariaDB)' }
    else { Mal "/actuator/health respondio $salud" 'Revisa la consola de la app' }

    try {
        $login = Invoke-RestMethod -Method Post -Uri "$Api/api/v1/auth/login" -ContentType 'application/json' `
            -Body (@{ username = 'admin'; password = 'admin123' } | ConvertTo-Json)
        if ($login.access_token) { Bien 'POST /api/v1/auth/login devuelve un token' }
        else { Mal 'El login de la API respondio pero sin access_token' }
    } catch {
        Mal 'POST /api/v1/auth/login fallo' $_.Exception.Message
    }

    $sinToken = Codigo-Http 'Get' "$Api/api/v1/categorias"
    if ($sinToken -eq 401) { Bien 'Sin token, la API responde 401' }
    else { Mal "Sin token, la API respondio $sinToken (se esperaba 401)" }

    if ($tokens['usuario'] -and $tokens['admin']) {
        $conToken = Codigo-Http 'Get' "$Api/api/v1/categorias" @{ Authorization = "Bearer $($tokens['usuario'])" }
        if ($conToken -eq 401) { Mal 'Con un token valido la API sigue diciendo 401' 'Revisa issuer-uri en application.yml' }
        elseif ($conToken -eq 404) {
            Bien 'Con token valido la API acepta la autenticacion (respondio 404)'
            Info '404 es normal mientras no exista CategoriaController (paso 7).'
        }
        else { Probar-Roles-Categoria; Probar-Productos }
    }

    # CORS (requisito 2, clave para el Lab 2): Angular en :4200 si, cualquier otro origen no
    Paso 'CORS (requisito 2)'
    $angular = 'http://localhost:4200'
    $pf = Preflight "$Api/api/v1/categorias" $angular
    if ($pf.codigo -eq 200 -and $pf.permitido -eq $angular) { Bien "CORS: el preflight desde $angular se acepta" }
    else { Mal "CORS: el preflight desde $angular respondio $($pf.codigo) (Allow-Origin: '$($pf.permitido)')" 'Revisa .cors(...) en SecurityConfig y app.cors.allowed-origins en application.yml' }

    $pf = Preflight "$Api/api/v1/categorias" 'http://sitio-ajeno.com'
    if ($pf.codigo -eq 403) { Bien 'CORS: un origen no autorizado se rechaza (403)' }
    else { Mal "CORS: un origen no autorizado respondio $($pf.codigo) (se esperaba 403)" 'allowed-origins no deberia ser *' }
}

# ---------------------------------------------------------------------------
Write-Host ''
$color = if ($script:fallos -eq 0) { 'Green' } else { 'Red' }
Write-Host "Resultado: $($script:ok) OK, $($script:fallos) fallas" -ForegroundColor $color
