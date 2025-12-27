# Instructions for Unrestricted Location Feature

To enable the "Unrestricted Location" feature for groups, you must update your Supabase database schema.

## 1. Run the SQL Script

Execute the contents of the file `ADD_LOCATION_RESTRICTION_TO_GROUPS.sql` in your Supabase SQL Editor.

This script adds a new column to the `groups` table:
- `is_location_restricted`: A boolean flag (default true) to determine if location checks are required.

## 2. Verify the Changes

After running the script, check the `groups` table in the Table Editor. You should see the `is_location_restricted` column.

## 3. App Functionality

- **Add/Edit Group:** Admins will now see a "Restrict Location" toggle switch.
- **Enabled (Default):** You must provide Latitude, Longitude, and Radius. Employees must be within this range to mark attendance.
- **Disabled:** Latitude, Longitude, and Radius fields are hidden. Employees in this group can mark attendance from anywhere (location check is skipped).

