-- Allow users to update their own profile
-- Run this in Supabase SQL Editor

CREATE POLICY "Users can update their own profile"
ON public.profiles
FOR UPDATE
USING (auth.uid()::text = id);

-- Ensure storage permissions are correct for profile-pictures
-- (Assuming bucket is already public, but we need insert/update permissions)
-- Note: Storage policies are separate from Table policies.

