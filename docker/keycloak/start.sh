#!/bin/bash
# Arranca Keycloak en modo desarrollo e importa el realm "veterinaria".
# Luego desactiva el requisito de HTTPS en el realm "master" (el de la consola de admin),
# porque Keycloak considera "externas" las peticiones que llegan desde fuera del contenedor.
# SOLO PARA DESARROLLO LOCAL.

/opt/keycloak/bin/kc.sh start-dev --import-realm &
KC_PID=$!
trap 'kill -TERM $KC_PID' TERM INT

KCADM=/opt/keycloak/bin/kcadm.sh
until $KCADM config credentials --server http://localhost:8080 --realm master \
        --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1; do
  sleep 2
done
$KCADM update realms/master -s sslRequired=NONE
echo ">>> Realm master: sslRequired=NONE (consola disponible en http://localhost:8180)"

wait $KC_PID
