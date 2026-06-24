CREATE TABLE users (
  id UUID PRIMARY KEY,
  display_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agents (
  id UUID PRIMARY KEY,
  owner_user_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(100) NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_intents (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  purpose TEXT NOT NULL,
  total_budget_amount NUMERIC(18, 6) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  max_amount_per_request NUMERIC(18, 6),
  require_approval_over NUMERIC(18, 6),
  allowed_merchants TEXT,
  blocked_merchants TEXT,
  allowed_categories TEXT,
  status VARCHAR(30) NOT NULL,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_requests (
  id UUID PRIMARY KEY,
  intent_id UUID NOT NULL REFERENCES payment_intents(id),
  agent_id UUID NOT NULL REFERENCES agents(id),
  quote_id VARCHAR(120),
  merchant VARCHAR(200) NOT NULL,
  resource TEXT NOT NULL,
  category VARCHAR(100),
  amount NUMERIC(18, 6) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  reason TEXT,
  quote_hash VARCHAR(120),
  status VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE policy_decisions (
  id UUID PRIMARY KEY,
  payment_request_id UUID NOT NULL REFERENCES payment_requests(id),
  decision VARCHAR(40) NOT NULL,
  reason_code VARCHAR(100) NOT NULL,
  reason_message TEXT,
  policy_version VARCHAR(50) NOT NULL,
  evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE approvals (
  id UUID PRIMARY KEY,
  payment_request_id UUID NOT NULL REFERENCES payment_requests(id),
  approver_user_id UUID NOT NULL REFERENCES users(id),
  decision VARCHAR(40) NOT NULL,
  comment TEXT,
  decided_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_results (
  id UUID PRIMARY KEY,
  payment_request_id UUID NOT NULL REFERENCES payment_requests(id),
  status VARCHAR(40) NOT NULL,
  simulated_transaction_id VARCHAR(120),
  failure_reason TEXT,
  paid_at TIMESTAMPTZ
);

CREATE TABLE audit_events (
  id UUID PRIMARY KEY,
  event_type VARCHAR(80) NOT NULL,
  subject_id UUID NOT NULL,
  canonical_json TEXT NOT NULL,
  event_hash VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_anchors (
  id UUID PRIMARY KEY,
  audit_event_id UUID NOT NULL REFERENCES audit_events(id),
  event_type VARCHAR(80) NOT NULL,
  event_hash VARCHAR(120) NOT NULL,
  chain_id VARCHAR(80),
  contract_address VARCHAR(120),
  tx_hash VARCHAR(120),
  anchored_at TIMESTAMPTZ,
  verify_status VARCHAR(40) NOT NULL
);

CREATE INDEX idx_agents_owner_user_id ON agents(owner_user_id);
CREATE INDEX idx_payment_intents_user_id ON payment_intents(user_id);
CREATE INDEX idx_payment_requests_intent_id ON payment_requests(intent_id);
CREATE INDEX idx_payment_requests_agent_id ON payment_requests(agent_id);
CREATE INDEX idx_payment_requests_status ON payment_requests(status);
CREATE INDEX idx_policy_decisions_payment_request_id ON policy_decisions(payment_request_id);
CREATE INDEX idx_approvals_payment_request_id ON approvals(payment_request_id);
CREATE INDEX idx_payment_results_payment_request_id ON payment_results(payment_request_id);
CREATE INDEX idx_audit_events_subject_id ON audit_events(subject_id);
CREATE INDEX idx_audit_events_event_hash ON audit_events(event_hash);
CREATE INDEX idx_audit_anchors_audit_event_id ON audit_anchors(audit_event_id);
