-- Knowledge QA is profile-driven.  Keep this seed idempotent so an existing
-- installation never loses its other model definitions or fallback chains.

INSERT INTO kma_model_profile (
    profile_code, name, capability, provider, model_name, base_url,
    dimension, timeout_seconds, secret_alias, fallback_profile_codes,
    enabled, default_profile, create_time, update_time
) VALUES (
    'deepseek-v4-flash', 'DeepSeek V4 Flash', 'llm', 'deepseek', 'deepseek-v4-flash',
    'https://api.deepseek.com', NULL, 90, 'KMA_DEEPSEEK_API_KEY', '[]'::jsonb,
    TRUE, FALSE, now(), now()
)
ON CONFLICT (profile_code) DO UPDATE SET
    name = EXCLUDED.name,
    provider = EXCLUDED.provider,
    model_name = EXCLUDED.model_name,
    base_url = EXCLUDED.base_url,
    timeout_seconds = EXCLUDED.timeout_seconds,
    secret_alias = EXCLUDED.secret_alias,
    enabled = TRUE,
    update_time = now();

-- There is exactly one active LLM default.  Other profiles remain available
-- for explicit selection and opt-in fallback chains.
UPDATE kma_model_profile
SET default_profile = FALSE, update_time = now()
WHERE capability = 'llm' AND default_profile = TRUE;

UPDATE kma_model_profile
SET default_profile = TRUE, update_time = now()
WHERE profile_code = 'deepseek-v4-flash' AND capability = 'llm';

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_profile_active_default_capability
    ON kma_model_profile(capability)
    WHERE default_profile = TRUE AND enabled = TRUE;
