# AppMonitorService.kt Explanation

## Overview
`AppMonitorService` is a foreground Android service that monitors app usage, enforces time limits, and manages cooling periods. It runs continuously in the background to track which apps are being used and enforce user-defined time restrictions.

## Architecture

### 1. Core Components

```kotlin
// Repositories (Data Layer)
- sessionRepository: Manages active app sessions
- configRepository: Stores app-specific settings (behavior, cooling period)
- coolingPeriodRepository: Tracks cooling periods
- monitoredAppsRepository: Tracks which apps are monitored/blocked

// System Services
- usageStatsManager: Detects which app is in foreground
- handler: Main thread handler for UI operations
- blockingHandler: Separate handler for blocking checks
```

### 2. Service Lifecycle

#### `onCreate()` (Lines 52-80)
Initialization sequence:
1. Creates all repositories (data layer)
2. Gets `UsageStatsManager` for detecting foreground apps
3. Sets up handlers (main thread for UI operations)
4. Creates notification channel for foreground service
5. Starts foreground service (required for background monitoring)
6. Starts blocking check loop (continuous enforcement)
7. Auto-starts monitoring when service is created

#### `onStartCommand()` (Lines 82-92)
Handles incoming intents:
- `START_MONITORING`: Starts the monitoring loop
- `STOP_MONITORING`: Stops monitoring
- `UPDATE_MONITORED_APPS`: Updates the list of monitored apps

Returns `START_STICKY` so the service restarts if killed by the system.

## Main Monitoring Flow

### 1. `startMonitoring()` (Lines 134-147)
- Sets `isMonitoring = true`
- Posts a recurring task every 1 second (`CHECK_INTERVAL`)
- Each iteration calls `checkForegroundApp()`

### 2. `checkForegroundApp()` (Lines 154-215)
Runs every second to:
1. Query usage events to find the current foreground app
2. Check all active sessions for expiration
3. If a monitored app is in foreground:
   - Check if it's blocked → block it immediately
   - Check if time expired → handle time up
   - If app changed → handle launch

### 3. `handleMonitoredAppLaunch()` (Lines 217-250)
When a monitored app is launched:
1. **Check if blocked** → block immediately
2. **Check if in cooling period** → show cooling dialog and block
3. **Check for active session**:
   - If expired → handle time up
   - If active → allow usage (timer continues)
   - If no session → show time selection dialog

## Time Management Flow

### When User Selects Time (Lines 252-284)
1. `showTimeSelectionDialog()` shows dialog with options (2, 5, 10, 20 minutes)
2. On selection:
   - Creates `AppSession` with start time and requested minutes
   - Stores in `sessionRepository`
   - Sends event to React Native
3. If cancelled → blocks the app

### When Time Expires (Lines 286-342)
`handleTimeUp()` handles two behaviors:

#### Behavior: "ask" (Lines 290-324)
- **If app is in foreground**: Shows time extension dialog
  - User grants time → extends session
  - User declines → blocks and starts cooling period
- **If app not in foreground**: Blocks immediately and starts cooling period

#### Behavior: "stop" (Lines 325-340)
- Immediately blocks the app
- Removes session
- Starts cooling period
- Re-checks after 500ms to ensure app stays closed

### Cooling Period (Lines 344-367)
`blockAppAndStartCooling()`:
1. Gets cooling period duration from config (default 30 minutes)
2. Calculates end time
3. Stores in `coolingPeriodRepository`
4. Shows cooling period dialog
5. Sends events to React Native
6. Schedules auto-unblock when cooling period ends

## Continuous Blocking Check

### `startBlockingCheck()` (Lines 369-425)
Runs every 2 seconds (`BLOCKING_CHECK_INTERVAL`):
1. Checks all sessions for expiration
2. If blocked app is in foreground → blocks it again
3. Checks cooling periods → unblocks when expired
4. Ensures blocked apps stay blocked

### `checkAllActiveSessions()` (Lines 427-460)
Checks all active sessions for expiration:
- If app is in foreground → shows dialog (if behavior is "ask")
- If app is not in foreground → blocks immediately

## Dialog Management

### Time Selection Dialog (Lines 252-284)
- Shown when app is first launched (no session)
- Shown when time expires and behavior is "ask"
- Options: 2, 5, 10, 20 minutes
- Runs on main thread to ensure UI operations are safe

### Cooling Period Dialog (Lines 462-477)
- Shown when app enters cooling period
- Displays countdown timer
- Auto-dismisses when cooling period ends
- Blocks app if user dismisses

## React Native Integration

### `sendEventToReactNative()` (Lines 488-491)
Sends events to React Native:
- `sessionStarted`: When user selects time
- `sessionExtended`: When user grants more time
- `setCoolingPeriod`: When cooling period starts
- `coolingPeriodStarted`: Cooling period notification

## Key Features

1. **Foreground Service**: Runs continuously with notification
2. **Real-time Monitoring**: Checks every 1 second
3. **Continuous Blocking**: Re-checks every 2 seconds
4. **Thread Safety**: UI operations on main thread
5. **State Management**: Uses repositories for data
6. **Error Handling**: Try-catch around critical operations

## Data Flow Example

```
User opens Instagram (monitored app)
  ↓
checkForegroundApp() detects it
  ↓
handleMonitoredAppLaunch() called
  ↓
No active session found
  ↓
showTimeSelectionDialog() → User selects 10 minutes
  ↓
Session created: startTime=now, requestedMinutes=10
  ↓
User uses app for 10 minutes
  ↓
checkForegroundApp() detects time expired
  ↓
handleTimeUp() → behavior="ask"
  ↓
TimeSelectionDialog shown again (extension)
  ↓
User selects 5 more minutes OR cancels
  ↓
If cancelled → blockAppAndStartCooling()
  ↓
App blocked, cooling period started (30 min default)
```

## Important Constants

- `CHECK_INTERVAL = 1000ms`: How often to check foreground app
- `BLOCKING_CHECK_INTERVAL = 2000ms`: How often to enforce blocking
- `pendingTimeUpChecks`: Prevents duplicate time-up dialogs

## Dependencies

- **Repositories**: `AppSessionRepository`, `AppConfigRepository`, `CoolingPeriodRepository`, `MonitoredAppsRepository`
- **UI Components**: `TimeSelectionDialog`, `CoolingPeriodDialog`
- **Utilities**: `AppBlocker`, `AppNameResolver`, `ForegroundAppDetector`, `NotificationHelper`
- **Model**: `AppSession`

## Threading Model

- **Main Thread (`handler`)**: Used for UI operations (showing dialogs)
- **Main Thread (`blockingHandler`)**: Used for blocking checks and enforcement
- **Background Thread**: Service runs in background, but UI operations are posted to main thread

## Error Handling

- Try-catch blocks around dialog showing operations
- Logging for debugging and monitoring
- Graceful fallbacks for permission issues

This service is the core of the app monitoring system, handling detection, time management, blocking, and user interactions.

