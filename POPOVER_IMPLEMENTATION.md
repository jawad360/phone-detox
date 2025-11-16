# Time Selection Popover Implementation

## Overview

This document describes the implementation of the time selection popover that appears when a monitored app is launched. The popover asks users to select how much time they want to use the app (5, 10, 15, 30, 60, 120, or 180 minutes).

## Architecture

The implementation consists of three main components:

### 1. Native Android Service (`AppMonitorService.kt`)
- **Location**: `android/app/src/main/java/com/techmania/phonedetox/AppMonitorService.kt`
- **Purpose**: Monitors app launches using Android's `UsageStatsManager`
- **Key Features**:
  - Runs as a foreground service to ensure it continues running in the background
  - Checks every second for app launches
  - Detects when monitored apps come to foreground
  - Manages active sessions and tracks time limits
  - Handles cooling periods
  - Shows time selection dialog when monitored apps launch

### 2. Time Selection Dialog (`TimeSelectionDialog.kt`)
- **Location**: `android/app/src/main/java/com/techmania/phonedetox/TimeSelectionDialog.kt`
- **Purpose**: Displays a system overlay dialog asking for time selection
- **Key Features**:
  - Shows as a system overlay (can appear over other apps)
  - Displays time options: 5, 10, 15, 30, 60, 120, 180 minutes
  - Handles both initial time selection and time extension requests
  - Programmatically created UI with semi-transparent background

### 3. React Native Bridge (`AppMonitorModule.kt` & `appMonitor.ts`)
- **Location**: 
  - Native: `android/app/src/main/java/com/techmania/phonedetox/AppMonitorModule.kt`
  - JS: `utils/appMonitor.ts`
- **Purpose**: Communication bridge between React Native and native Android code
- **Key Features**:
  - Starts/stops monitoring service
  - Updates list of monitored apps
  - Updates app configurations (behavior, cooling period)
  - Handles events from native code (session started, expired, cooling period started)

## Flow

### When a Monitored App Launches:

1. **AppMonitorService** detects the app launch via `UsageStatsManager`
2. Checks if app is in cooling period:
   - If yes → Blocks the app immediately
   - If no → Continues to step 3
3. Checks if there's an active session:
   - If session exists and time is up → Handles timeout based on behavior
   - If no session → Shows time selection dialog
4. **TimeSelectionDialog** appears with time options
5. User selects time or cancels:
   - If time selected → Starts session, allows app to continue
   - If cancelled → Blocks the app
6. Service tracks the session and monitors time
7. When time is up:
   - If behavior is "ask" → Shows dialog again asking for more time
   - If behavior is "stop" → Blocks app and starts cooling period

## Integration Points

### React Native Screens:

1. **`app/(tabs)/index.tsx`**:
   - Initializes monitoring on mount
   - Updates monitoring service when apps list changes
   - Syncs with service when apps are removed

2. **`app/add-app.tsx`**:
   - Updates monitoring service when new app is added

3. **`app/app-settings.tsx`**:
   - Updates monitoring service when app settings change (behavior, cooling period)

## Permissions Required

The implementation requires the following Android permissions (already declared in `AndroidManifest.xml`):

- `PACKAGE_USAGE_STATS` - To monitor which apps are launched
- `SYSTEM_ALERT_WINDOW` - To show overlay dialogs over other apps
- `FOREGROUND_SERVICE` - To run the monitoring service in the background

## Configuration

### Time Options
The dialog shows these time options (defined in `TimeSelectionDialog.kt`):
- 5 minutes
- 10 minutes
- 15 minutes
- 30 minutes
- 60 minutes (1 hour)
- 120 minutes (2 hours)
- 180 minutes (3 hours)

### App Behaviors
- **"ask"**: When time is up, shows dialog asking for more time
- **"stop"**: When time is up, immediately blocks app and starts cooling period

## Testing

To test the implementation:

1. Build the app with development client:
   ```bash
   npx expo run:android
   ```

2. Grant required permissions:
   - Usage Access
   - Display over other apps
   - Battery optimization (optional but recommended)

3. Add an app to monitor

4. Launch the monitored app - the popover should appear

5. Select a time option - the app should continue

6. Wait for time to expire (or manually trigger) - behavior should match settings

## Files Created/Modified

### New Files:
- `android/app/src/main/java/com/techmania/phonedetox/AppMonitorService.kt`
- `android/app/src/main/java/com/techmania/phonedetox/TimeSelectionDialog.kt`
- `android/app/src/main/java/com/techmania/phonedetox/AppMonitorModule.kt`
- `android/app/src/main/java/com/techmania/phonedetox/AppMonitorPackage.kt`
- `utils/appMonitor.ts`

### Modified Files:
- `android/app/src/main/AndroidManifest.xml` - Added service declaration
- `android/app/src/main/java/com/techmania/phonedetox/MainApplication.kt` - Registered package
- `app/(tabs)/index.tsx` - Integrated monitoring manager
- `app/add-app.tsx` - Sync with monitoring service
- `app/app-settings.tsx` - Sync with monitoring service

## Notes

- The service runs as a foreground service to ensure it continues working even when the app is in the background
- The dialog uses `TYPE_APPLICATION_OVERLAY` (Android 8+) or `TYPE_SYSTEM_ALERT` (older versions) to appear over other apps
- Cooling periods are stored in React Native storage and synced with the native service
- The service emits events to React Native for session tracking and storage

