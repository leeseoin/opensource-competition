CREATE ROLE agentpay_guard_seoin WITH LOGIN PASSWORD 'agentpay_guard';
CREATE ROLE agentpay_guard_jeongwoo WITH LOGIN PASSWORD 'agentpay_guard';

GRANT CONNECT ON DATABASE agentpay_guard TO agentpay_guard_seoin;
GRANT CONNECT ON DATABASE agentpay_guard TO agentpay_guard_jeongwoo;

\connect agentpay_guard

GRANT USAGE, CREATE ON SCHEMA public TO agentpay_guard_seoin;
GRANT USAGE, CREATE ON SCHEMA public TO agentpay_guard_jeongwoo;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO agentpay_guard_seoin;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO agentpay_guard_jeongwoo;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO agentpay_guard_seoin;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO agentpay_guard_jeongwoo;
