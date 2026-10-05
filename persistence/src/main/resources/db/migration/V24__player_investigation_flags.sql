CREATE TABLE player_investigation_flags (
    flag_id BINARY(16) PRIMARY KEY,
    target_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    category VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    expires_at TIMESTAMP(3) NULL,
    case_id VARCHAR(16) NULL,
    resolved_at TIMESTAMP(3) NULL,
    INDEX ix_player_flags_active (target_id, resolved_at, expires_at, created_at)
);

CREATE TABLE player_investigation_flag_audit (
    audit_id BINARY(16) PRIMARY KEY,
    flag_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    INDEX ix_player_flag_audit (flag_id, occurred_at),
    CONSTRAINT fk_player_flag_audit FOREIGN KEY (flag_id) REFERENCES player_investigation_flags(flag_id)
);
