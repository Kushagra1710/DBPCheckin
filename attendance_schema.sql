-- Create a bucket for attendance proofs
insert into storage.buckets (id, name, public) values ('attendance-proofs', 'attendance-proofs', true);

-- Create attendance table
create table public.attendance (
  id uuid default gen_random_uuid() primary key,
  user_id uuid references auth.users(id) not null,
  timestamp timestamptz default now() not null,
  image_url text,
  status text default 'present',
  location text -- Optional: for storing lat,long
);

-- Enable RLS
alter table public.attendance enable row level security;

-- Policies
create policy "Users can create their own attendance records."
  on public.attendance for insert
  with check (auth.uid() = user_id);

create policy "Users can view their own attendance records."
  on public.attendance for select
  using (auth.uid() = user_id);

create policy "Admins can view all attendance records."
  on public.attendance for select
  using (
    exists (
      select 1 from public.profiles
      where profiles.id = auth.uid() and profiles.role = 'admin'
    )
  );

-- Storage policies
create policy "Users can upload attendance proofs"
  on storage.objects for insert
  with check (bucket_id = 'attendance-proofs' and auth.uid() = (storage.foldername(name))[1]::uuid);

create policy "Users can view their own attendance proofs"
  on storage.objects for select
  using (bucket_id = 'attendance-proofs' and auth.uid() = (storage.foldername(name))[1]::uuid);

