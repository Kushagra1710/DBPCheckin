-- Create a stored procedure (RPC) to assign a user to a group
-- This helps bypass potential client-side filtering issues or RLS weirdness by running as a function.

CREATE OR REPLACE FUNCTION assign_user_to_group(user_id text, new_group_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER -- Runs with the privileges of the creator (admin)
AS $$
BEGIN
  UPDATE public.profiles
  SET group_id = new_group_id
  WHERE id = user_id;
END;
$$;

