-- RESET SCHEMA FOR FIREBASE AUTH SUPPORT
-- WARNING: THIS WILL DELETE ALL DATA IN THESE TABLES

-- 1. Drop existing tables to ensure clean slate
DROP TABLE IF EXISTS public.attendance;
DROP TABLE IF EXISTS public.profiles;
DROP TABLE IF EXISTS public.groups;

-- 2. Re-create 'groups' table
CREATE TABLE public.groups (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  name text NOT NULL,
  latitude double precision NOT NULL,
  longitude double precision NOT NULL,
  start_time text NULL,
  end_time text NULL,
  CONSTRAINT groups_pkey PRIMARY KEY (id)
);

-- 3. Re-create 'profiles' table
-- 'id' must be TEXT to support Firebase UIDs
CREATE TABLE public.profiles (
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

-- 4. Re-create 'attendance' table
-- 'user_id' must be TEXT to support Firebase UIDs
CREATE TABLE public.attendance (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  user_id text NOT NULL,
  image_url text NULL,
  status text DEFAULT 'present',
  location text NULL,
  CONSTRAINT attendance_pkey PRIMARY KEY (id),
  CONSTRAINT attendance_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE
);

-- 5. Ensure Storage Buckets exist
INSERT INTO storage.buckets (id, name, public)
VALUES ('profile-pictures', 'profile-pictures', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public)
VALUES ('attendance-proofs', 'attendance-proofs', true)
ON CONFLICT (id) DO NOTHING;

-- 6. Disable RLS for Firebase Auth compatibility
ALTER TABLE public.groups DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance DISABLE ROW LEVEL SECURITY;

-- 7. Storage Policies
DROP POLICY IF EXISTS "Public Access" ON storage.objects;
CREATE POLICY "Public Access" ON storage.objects FOR ALL USING (true) WITH CHECK (true);

