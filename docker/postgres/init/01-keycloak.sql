-- Keycloak은 결제 DB와 같은 PostgreSQL 서버의 별도 DB를 쓴다. 이미 있으면 건너뛴다.
SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
