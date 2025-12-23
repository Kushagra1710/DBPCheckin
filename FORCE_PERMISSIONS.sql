-- FORCE DISABLE RLS AND GRANT PERMISSIONS
-- Run this in Supabase SQL Editor to ensure updates are allowed

ALTER TABLE public.profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance DISABLE ROW LEVEL SECURITY;

GRANT ALL ON public.profiles TO anon;
GRANT ALL ON public.profiles TO authenticated;
GRANT ALL ON public.profiles TO service_role;

GRANT ALL ON public.groups TO anon;
GRANT ALL ON public.groups TO authenticated;
GRANT ALL ON public.groups TO service_role;

-- Verify column types
-- id should be text
-- group_id should be uuid

