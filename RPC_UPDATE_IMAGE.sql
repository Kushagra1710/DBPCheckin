-- Create a secure function to update any profile image by ID
-- This is necessary because the app uses Firebase Auth but Supabase DB,
-- so standard RLS based on auth.uid() might not work if the Supabase client isn't authenticated.

CREATE OR REPLACE FUNCTION update_profile_image_by_id(p_user_id text, p_image_url text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE public.profiles
  SET image_url = p_image_url
  WHERE id = p_user_id;
END;
$$;

