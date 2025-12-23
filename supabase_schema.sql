-- 1. Create the 'groups' table
-- This table stores information about different employee groups.
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

-- 2. Create the 'profiles' table
-- This table stores public user data, linked to the authentication users.
CREATE TABLE public.profiles (
  id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  name text NULL,
  email text NULL,
  phone text NULL,
  tehsil text NULL,
  image_url text NULL,
  role text NOT NULL DEFAULT 'employee'::text,
  group_id uuid NULL,
  CONSTRAINT profiles_pkey PRIMARY KEY (id),
  CONSTRAINT profiles_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(id) ON DELETE SET NULL,
  CONSTRAINT profiles_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE
);

-- 3. Create the 'attendance' table
-- This table will store each check-in record.
CREATE TABLE public.attendance (
  id bigserial NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  user_id uuid NOT NULL,
  group_id uuid NOT NULL,
  CONSTRAINT attendance_pkey PRIMARY KEY (id),
  CONSTRAINT attendance_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(id) ON DELETE CASCADE,
  CONSTRAINT attendance_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE
);


-- 4. Set up Row Level Security (RLS)
-- Enable RLS for all tables
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance ENABLE ROW LEVEL SECURITY;

-- 5. Create RLS Policies

-- Policies for 'profiles' table
CREATE POLICY "Users can view their own profile." ON public.profiles FOR SELECT USING (auth.uid() = id);
CREATE POLICY "Users can update their own profile." ON public.profiles FOR UPDATE USING (auth.uid() = id);
-- Note: An admin role check is needed for admins to see all profiles.
-- This requires a function to check the user's role.
CREATE OR REPLACE FUNCTION get_my_role()
RETURNS TEXT AS $$
BEGIN
  RETURN (
    SELECT role
    FROM public.profiles
    WHERE id = auth.uid()
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
CREATE POLICY "Admins can view all profiles." ON public.profiles FOR SELECT USING (get_my_role() = 'admin');


-- Policies for 'groups' table
CREATE POLICY "Authenticated users can view all groups." ON public.groups FOR SELECT USING (auth.role() = 'authenticated');
CREATE POLICY "Admins can manage groups." ON public.groups FOR ALL USING (get_my_role() = 'admin');


-- Policies for 'attendance' table
CREATE POLICY "Users can create their own attendance records." ON public.attendance FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can view their own attendance records." ON public.attendance FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Admins can view all attendance records." ON public.attendance FOR SELECT USING (get_my_role() = 'admin');


-- 6. Create Storage Bucket for Profile Pictures
-- This bucket is for public images, so users can see each other's profile photos.
INSERT INTO storage.buckets (id, name, public)
VALUES ('profile-pictures', 'profile-pictures', true)
ON CONFLICT (id) DO NOTHING;

-- Create policies for the storage bucket
CREATE POLICY "Allow public read access to profile pictures" ON storage.objects FOR SELECT USING (bucket_id = 'profile-pictures');
CREATE POLICY "Allow users to upload their own profile picture" ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'profile-pictures' AND auth.uid() = (storage.foldername(name))[1]::uuid);
CREATE POLICY "Allow users to update their own profile picture" ON storage.objects FOR UPDATE USING (bucket_id = 'profile-pictures' AND auth.uid() = (storage.foldername(name))[1]::uuid);

-- Function to automatically create a profile when a new user signs up in auth.users
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, email, role)
  VALUES (new.id, new.email, 'employee');
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to execute the function after a new user is created
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

