# Instructions for Group Requests Feature

To enable the Group Requests feature (where employees can request to join a group during signup), you must update your Supabase database schema.

## 1. Run the SQL Script

Execute the contents of the file `ADD_GROUP_REQUESTS.sql` in your Supabase SQL Editor.

This script adds two new columns to the `profiles` table:
- `requested_group_id`: Stores the ID of the group the user wants to join.
- `request_status`: Tracks the status of the request ('none', 'pending', 'approved', 'rejected').

## 2. Verify the Changes

After running the script, you can verify the changes by checking the `profiles` table in the Table Editor. You should see the new columns.

## 3. App Functionality

- **Sign Up:** New users can now select a group from the dropdown. This will set their status to 'pending'.
- **Admin Dashboard:** Admins can go to "Group Requests" to see a list of pending users.
- **Approval:** Clicking the checkmark (✅) approves the user and assigns them to the group.
- **Rejection:** Clicking the cross (❌) rejects the request.
- **Employee Home:** Employees will see "Request Pending" or "Request Rejected" status on their home screen until approved.

