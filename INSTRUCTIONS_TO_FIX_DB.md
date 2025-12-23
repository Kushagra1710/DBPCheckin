# CRITICAL: Fix Database Schema

The "profile creation error" or "invalid input syntax for uuid" happens because the database expects a UUID for the `id` column, but Firebase provides a text-based UID (e.g., "abc123xyz").

**You MUST run the following SQL script in your Supabase Dashboard to fix this.**

1.  Go to **Supabase Dashboard** -> **SQL Editor**.
2.  Click **New Query**.
3.  Copy and paste the code below.
4.  Click **Run**.

```sql
-- 1. Drop existing tables to ensure clean slate (WARNING: DELETES DATA)
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

-- 3. Re-create 'profiles' table with ID as TEXT
CREATE TABLE public.profiles (
  id text NOT NULL, -- Changed from UUID to TEXT for Firebase compatibility
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

-- 4. Re-create 'attendance' table with USER_ID as TEXT
CREATE TABLE public.attendance (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  user_id text NOT NULL, -- Changed from UUID to TEXT
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
```

After running this, try creating a new account in the app.

