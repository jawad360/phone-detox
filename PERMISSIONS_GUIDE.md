# Permissions Setup Guide

## Overview

The Phone Detox app now includes a comprehensive permissions flow that runs on first launch. This ensures users grant all necessary permissions for the app to function properly.

## Permissions Required

### 1. Usage Access (Required)
- **Purpose**: Monitor which apps users open and track usage time
- **Android Permission**: `android.permission.PACKAGE_USAGE_STATS`
- **User Action**: Enable in Settings > Apps > Special access > Usage access

### 2. Display Over Other Apps (Required)
- **Purpose**: Show prompts when users try to open monitored apps
- **Android Permission**: `android.permission.SYSTEM_ALERT_WINDOW`
- **User Action**: Enable in Settings > Apps > Special access > Display over other apps

### 3. Battery Optimization (Recommended)
- **Purpose**: Ensure the app works in the background
- **Android Permission**: `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **User Action**: Disable battery optimization in Settings > Apps > Special access > Battery optimization

### Other Permissions (Declared in Manifest)
- `android.permission.FOREGROUND_SERVICE` - Run background services
- `android.permission.WAKE_LOCK` - Keep the device awake when needed
- `android.permission.QUERY_ALL_PACKAGES` - Query installed apps

## User Flow

### First Launch
1. App checks if permissions have been requested before
2. If not, user is shown the **Permissions Screen**
3. User sees a beautiful UI explaining each permission
4. User taps "Grant Permissions"
5. App opens each permission settings page sequentially:
   - Usage Access Settings
   - Display Over Other Apps Settings
   - Battery Optimization Settings
6. User grants permissions in system settings
7. User is navigated to the main app

### Skipping Permissions
- Users can tap "Skip for Now" to use the app with limited functionality
- They can grant permissions later from the Settings tab

## Implementation Details

### Files Created/Modified

1. **`app/permissions.tsx`** (NEW)
   - Beautiful permissions screen with cards for each permission
   - Explains why each permission is needed
   - Sequential permission request flow

2. **`app/index.tsx`** (MODIFIED)
   - Checks AsyncStorage for permission status
   - Redirects to permissions screen or main app accordingly

3. **`app/_layout.tsx`** (MODIFIED)
   - Added permissions screen to navigation stack
   - Disabled gestures on permissions screen (can't swipe away)

4. **`app/(tabs)/settings.tsx`** (MODIFIED)
   - Added battery optimization option
   - Fixed overlay permission to use correct intent

### AsyncStorage Key
- `@permissions_granted` - Stores permission status ("true" or "skipped")

### Permission Request Methods

```javascript
// Usage Access
IntentLauncher.startActivityAsync(
  IntentLauncher.ActivityAction.USAGE_ACCESS_SETTINGS
);

// Display Over Other Apps
IntentLauncher.startActivityAsync(
  IntentLauncher.ActivityAction.MANAGE_OVERLAY_PERMISSION
);

// Battery Optimization
IntentLauncher.startActivityAsync(
  IntentLauncher.ActivityAction.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
);
```

## Testing

### First Launch Experience
1. Clear app data or uninstall/reinstall
2. Launch the app
3. Verify permissions screen appears
4. Test "Grant Permissions" flow
5. Test "Skip for Now" flow

### Settings Access
1. Navigate to Settings tab
2. Verify all three permission cards are visible
3. Tap each card and verify correct settings page opens

## Future Enhancements

1. **Permission Status Check**: Add runtime checks to verify if permissions are actually granted
2. **Permission Icons**: Show checkmarks or warnings based on actual permission status
3. **Inline Permission Request**: Show permission prompts when user tries to use features requiring them
4. **Persistent Reminders**: Remind users who skipped permissions to grant them

## Notes

- Permissions are requested sequentially with 1-second delays to avoid overwhelming users
- Users cannot swipe away the permissions screen (gestureEnabled: false)
- Settings tab always provides access to permission settings
- The app gracefully handles permission denial with fallback modes

