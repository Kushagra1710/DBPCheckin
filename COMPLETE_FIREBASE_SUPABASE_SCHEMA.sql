-- COMPLETE SCHEMA FOR FIREBASE AUTH + SUPABASE DATABASE
-- Run this in your Supabase SQL Editor

-- 1. Create the 'groups' table
CREATE TABLE IF NOT EXISTS public.groups (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  name text NOT NULL,
  latitude double precision NOT NULL,
  longitude double precision NOT NULL,
  start_time text NULL,
  end_time text NULL,
  CONSTRAINT groups_pkey PRIMARY KEY (id)
);

-- 2. Create the 'profiles' table
-- Note: 'id' is TEXT to accommodate Firebase UIDs.
CREATE TABLE IF NOT EXISTS public.profiles (
  id text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  name text NULL,
  email text NULL,
  phone text NULL,
  tehsil text NULL,
  image_url text NULL,
  role text NOT NULL DEFAULT 'employee'::text,
  group_id uuid NULL,
  CONSTRAINT profiles_pkey PRIMARY KEY (id),
  CONSTRAINT profiles_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(id) ON DELETE SET NULL
);

-- 3. Create the 'attendance' table
CREATE TABLE IF NOT EXISTS public.attendance (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  user_id text NOT NULL,
  image_url text NULL,
  status text DEFAULT 'present',
  location text NULL,
  CONSTRAINT attendance_pkey PRIMARY KEY (id),
  CONSTRAINT attendance_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE
);

-- 4. Create Storage Buckets
INSERT INTO storage.buckets (id, name, public)
VALUES ('profile-pictures', 'profile-pictures', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public)
VALUES ('attendance-proofs', 'attendance-proofs', true)
ON CONFLICT (id) DO NOTHING;

-- 5. Disable RLS for now (since we are using Firebase Auth and not Supabase Auth)
-- This allows the app to read/write data without Supabase Auth tokens.
-- WARNING: This makes the database public. For production, you should implement
-- a mechanism to verify Firebase tokens in Supabase (e.g. via Edge Functions)
-- or use a proxy. For this prototype, disabling RLS is the quickest path.

ALTER TABLE public.groups DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance DISABLE ROW LEVEL SECURITY;

-- 6. Storage Policies (Open access for now)
DROP POLICY IF EXISTS "Public Access" ON storage.objects;
CREATE POLICY "Public Access" ON storage.objects FOR ALL USING (true) WITH CHECK (true);

