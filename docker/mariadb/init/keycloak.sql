-- BD propia de Keycloak, separada de la base lab1 de la app
CREATE DATABASE keycloak CHARACTER SET utf8mb4;

-- Usuario que usa Keycloak para conectarse a su base
CREATE USER 'keycloak'@'%' IDENTIFIED BY 'keycloak';

-- Le doy permiso solo sobre su base, no sobre lab1
GRANT ALL PRIVILEGES ON keycloak.* TO 'keycloak'@'%';