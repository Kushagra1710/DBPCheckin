-- Add radius column to groups table
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS radius double precision DEFAULT 100.0;

