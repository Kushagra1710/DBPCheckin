# Firebase Setup Instructions

The "Firestore Permission Denied" error occurs because the Firestore Security Rules have not been updated to allow the app to write data.

## 1. Enable Firestore
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Select your project (`attendiy-d217b`).
3. In the left menu, click **Build** > **Firestore Database**.
4. Click **Create Database**.
5. Select a location (e.g., `nam5 (us-central)`).
6. Start in **Production mode**.

## 2. Update Security Rules
1. In the Firestore Database section, click on the **Rules** tab.
2. Replace the existing rules with the following:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // User profiles
    match /users/{userId} {
      // Users can read and write their own profile
      allow read, write: if request.auth != null && request.auth.uid == userId;
      // Admins can read all profiles (to manage them) and write (to update roles/groups)
      allow read, write: if request.auth != null && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }
    
    // Groups
    match /groups/{groupId} {
      // Authenticated users can read groups (e.g., to see their assigned group)
      allow read: if request.auth != null;
      // Only admins can create, update, or delete groups
      allow write: if request.auth != null && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }

    // Default deny
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

3. Click **Publish**.

## 3. Create an Admin User
Since the app defaults new users to "employee", you need to manually set an admin:
1. Sign up a new user in the app.
2. Go to **Firestore Database** > **Data** tab.
3. Find the `users` collection and the document for the user you just created.
4. Change the `role` field from `"employee"` to `"admin"`.
5. Sign out and sign back in on the app to see the Admin Dashboard.

