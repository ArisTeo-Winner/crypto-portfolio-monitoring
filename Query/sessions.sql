CREATE TABLE IF NOT EXISTS public.sessions (
    session_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    ip_address INET,
    user_agent TEXT,
    login_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    logout_time TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT TRUE,
    CONSTRAINT sessions_pkey PRIMARY KEY (session_id),
    CONSTRAINT sessions_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

-- Índices para auditoría
CREATE INDEX idx_sessions_user_id ON public.sessions (user_id);
CREATE INDEX idx_sessions_login_time ON public.sessions (login_time);
CREATE INDEX idx_sessions_is_active ON public.sessions (is_active);