# AppMonitorService.kt Explanation

## Overview
`AppMonitorService` is a foreground Android service that monitors app usage, enforces time limits, and manages cooling periods. It runs continuously in the background to track which apps are being used and enforce user-defined time restrictions.

The service has been refactored for better readability, maintainability, and code reusability with clear separation of concerns.

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

### 2. Constants

The service uses constants for better maintainability:

```kotlin
// Behavior Types
- BEHAVIOR_ASK = "ask"  // Ask user for more time when limit reached
- BEHAVIOR_STOP = "stop" // Stop app immediately when limit reached

// React Native Event Names
- EVENT_SESSION_STARTED
- EVENT_SESSION_EXTENDED
- EVENT_SET_COOLING_PERIOD
- EVENT_COOLING_PERIOD_STARTED

// Timing Constants
- CHECK_INTERVAL = 1000ms (foreground app detection)
- BLOCKING_CHECK_INTERVAL = 2000ms (blocking enforcement)
- RE_BLOCK_DELAY_MS = 500ms (re-blocking delay)
- DEFAULT_COOLING_MINUTES = 30
```

### 3. Service Lifecycle

#### `onCreate()` (Lines 60-72)
Initialization sequence (refactored into separate methods):
1. `initializeRepositories()` - Creates all repositories (data layer)
2. `initializeServices()` - Gets UsageStatsManager and sets up handlers
3. `setupNotifications()` - Creates notification channel for foreground service
4. `startForegroundService()` - Starts foreground service (required for background monitoring)
5. `startBlockingCheck()` - Starts blocking check loop (continuous enforcement)
6. `startMonitoring()` - Auto-starts monitoring when service is created

#### `onStartCommand()` (Lines 74-85)
Handles incoming intents:
- `ACTION_START_MONITORING`: Starts the monitoring loop
- `ACTION_STOP_MONITORING`: Stops monitoring
- `ACTION_UPDATE_MONITORED_APPS`: Updates the list of monitored apps

Returns `START_STICKY` so the service restarts if killed by the system.

## Main Monitoring Flow

### 1. `startMonitoring()` (Lines 134-147)
- Sets `isMonitoring = true`
- Posts a recurring task every 1 second (`CHECK_INTERVAL`)
- Each iteration calls `checkForegroundApp()`

### 2. `checkForegroundApp()` (Lines 149-165)
Runs every second to:
1. `detectForegroundApp()` - Query usage events to find the current foreground app
2. `checkAllActiveSessions()` - Check all active sessions for expiration
3. `handleForegroundApp()` - Process the foreground app:
   - `handleBlockedApp()` - Check if blocked → block it immediately
   - `checkExpiredSessionInForeground()` - Check if time expired → handle time up
   - `handleAppChange()` - If app changed → handle launch

### 3. `handleMonitoredAppLaunch()` (Lines 224-240)
When a monitored app is launched (refactored into focused methods):
1. `handleBlockedAppOnLaunch()` - Check if blocked → block immediately
2. `handleCoolingPeriodOnLaunch()` - Check if in cooling period → show cooling dialog and block
3. `handleSessionOnLaunch()` - Check for active session:
   - If expired → handle time up
   - If active → allow usage (timer continues)
   - If no session → show time selection dialog

## Time Management Flow

### When User Selects Time (Lines 242-280)
1. `showTimeSelectionDialog()` shows dialog with options (2, 5, 10, 20 minutes)
2. `handleTimeSelection()` processes the selection:
   - If time selected → `startNewSession()`:
     - Creates `AppSession` with start time and requested minutes
     - Stores in `sessionRepository`
     - Sends `EVENT_SESSION_STARTED` to React Native
   - If cancelled → blocks the app

### When Time Expires (Lines 282-328)
`handleTimeUp()` delegates to behavior-specific handlers:

#### Behavior: "ask" → `handleAskBehaviorTimeUp()` (Lines 330-338)
- **If app is in foreground**: 
  - `showTimeExtensionDialog()` - Shows time extension dialog
  - `handleTimeExtension()` processes response:
    - User grants time → `extendSession()` - Extends session
    - User declines → `handleUserDeclinedExtension()` - Blocks and starts cooling period
- **If app not in foreground**: 
  - `handleAskBehaviorTimeUpInBackground()` - Blocks immediately and starts cooling period

#### Behavior: "stop" → `handleStopBehaviorTimeUp()` (Lines 340-350)
- `blockAppImmediately()` - Immediately blocks the app
- Removes session
- `scheduleReBlockingIfNeeded()` - Re-checks after 500ms to ensure app stays closed
- `blockAppAndStartCooling()` - Starts cooling period

### Cooling Period (Lines 352-407)
`blockAppAndStartCooling()` (refactored into smaller methods):
1. `getCoolingPeriodMinutes()` - Gets cooling period duration from config (default 30 minutes)
2. Calculates end time
3. Stores in `coolingPeriodRepository`
4. `showCoolingPeriodDialog()` - Shows cooling period dialog
5. `notifyCoolingPeriodStarted()` - Sends events to React Native
6. `scheduleCoolingPeriodEnd()` - Schedules auto-unblock when cooling period ends
7. `endCoolingPeriod()` - Auto-executes when cooling period expires

## Continuous Blocking Check

### `startBlockingCheck()` (Lines 409-423)
Runs every 2 seconds (`BLOCKING_CHECK_INTERVAL`):
1. `checkExpiredSessionsInBlockingLoop()` - Checks all sessions for expiration
2. `enforceBlockedApps()` - If blocked app is in foreground → blocks it again
3. `unblockExpiredCoolingPeriods()` - Checks cooling periods → unblocks when expired
4. Ensures blocked apps stay blocked

### `checkAllActiveSessions()` (Lines 425-447)
Refactored into focused methods:
1. `findExpiredSessions()` - Finds all expired sessions
2. For each expired session:
   - If app is in foreground → `handleExpiredSessionInForeground()` - Shows dialog (if behavior is "ask")
   - If app is not in foreground → `handleExpiredSessionInBackground()` - Blocks immediately

## Dialog Management

### Time Selection Dialog (Lines 242-280)
- `showTimeSelectionDialog()` - Shown when app is first launched (no session)
- `showTimeExtensionDialog()` - Shown when time expires and behavior is "ask"
- Options: 2, 5, 10, 20 minutes
- Runs on main thread to ensure UI operations are safe
- `handleTimeSelection()` / `handleTimeExtension()` - Process user responses

### Cooling Period Dialog (Lines 389-407)
- `showCoolingPeriodDialog()` - Shown when app enters cooling period
- Displays countdown timer
- `scheduleCoolingPeriodEnd()` - Auto-dismisses when cooling period ends
- Blocks app if user dismisses

## React Native Integration

### `sendEventToReactNative()` (Lines 609-612)
Sends events to React Native using constants:
- `EVENT_SESSION_STARTED`: When user selects time
- `EVENT_SESSION_EXTENDED`: When user grants more time
- `EVENT_SET_COOLING_PERIOD`: When cooling period starts
- `EVENT_COOLING_PERIOD_STARTED`: Cooling period notification

### `createSessionMap()` (Lines 604-611)
Creates a WritableMap from AppSession for React Native events.

## Code Organization

The refactored code is organized into logical sections:

### Initialization (Lines 87-132)
- `initializeRepositories()`
- `initializeServices()`
- `setupNotifications()`
- `startForegroundService()`

### Monitoring (Lines 134-165)
- `startMonitoring()` / `stopMonitoring()`
- `checkForegroundApp()`
- `detectForegroundApp()`
- `handleForegroundApp()`

### App Launch Handling (Lines 167-240)
- `handleMonitoredAppLaunch()`
- `handleBlockedAppOnLaunch()`
- `handleCoolingPeriodOnLaunch()`
- `handleSessionOnLaunch()`

### Dialog Management (Lines 242-367)
- `showTimeSelectionDialog()`
- `showTimeExtensionDialog()`
- `handleTimeSelection()`
- `handleTimeExtension()`
- `startNewSession()`
- `extendSession()`
- `handleUserDeclinedExtension()`

### Time Up Handling (Lines 282-350)
- `handleTimeUp()`
- `handleAskBehaviorTimeUp()`
- `handleStopBehaviorTimeUp()`
- `blockAppImmediately()`
- `scheduleReBlockingIfNeeded()`

### Cooling Period Management (Lines 352-407)
- `blockAppAndStartCooling()`
- `getCoolingPeriodMinutes()`
- `notifyCoolingPeriodStarted()`
- `showCoolingPeriodDialog()`
- `scheduleCoolingPeriodEnd()`
- `endCoolingPeriod()`

### Session Expiration Checking (Lines 425-447)
- `checkAllActiveSessions()`
- `findExpiredSessions()`
- `handleExpiredSessionInForeground()`
- `handleExpiredSessionInBackground()`

### Blocking Check Loop (Lines 409-595)
- `startBlockingCheck()`
- `checkExpiredSessionsInBlockingLoop()`
- `isSessionExpired()`
- `enforceBlockedApps()`
- `unblockExpiredCoolingPeriods()`
- `findAppsToUnblock()`
- `shouldUnblockApp()`

### React Native Communication (Lines 604-612)
- `createSessionMap()`
- `sendEventToReactNative()`

## Key Features

1. **Foreground Service**: Runs continuously with notification
2. **Real-time Monitoring**: Checks every 1 second
3. **Continuous Blocking**: Re-checks every 2 seconds
4. **Thread Safety**: UI operations on main thread
5. **State Management**: Uses repositories for data
6. **Error Handling**: Try-catch around critical operations
7. **Modular Design**: Small, focused methods for better maintainability
8. **Constants**: Magic strings replaced with named constants
9. **Separation of Concerns**: Each method has a single responsibility

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
startNewSession() → Session created: startTime=now, requestedMinutes=10
  ↓
User uses app for 10 minutes
  ↓
checkForegroundApp() detects time expired
  ↓
handleTimeUp() → behavior="ask"
  ↓
showTimeExtensionDialog() shown again (extension)
  ↓
handleTimeExtension() → User selects 5 more minutes OR cancels
  ↓
If cancelled → handleUserDeclinedExtension() → blockAppAndStartCooling()
  ↓
App blocked, cooling period started (30 min default)
```

## Important Constants

- `CHECK_INTERVAL = 1000ms`: How often to check foreground app
- `BLOCKING_CHECK_INTERVAL = 2000ms`: How often to enforce blocking
- `RE_BLOCK_DELAY_MS = 500ms`: Delay before re-blocking check
- `DEFAULT_COOLING_MINUTES = 30`: Default cooling period duration
- `BEHAVIOR_ASK`: Ask user for more time
- `BEHAVIOR_STOP`: Stop app immediately
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
- Consistent error handling patterns throughout

## Refactoring Benefits

The refactored code provides:

1. **Better Readability**: Smaller, focused methods with clear names
2. **Improved Maintainability**: Changes are localized to specific methods
3. **Code Reusability**: Common operations extracted into reusable methods
4. **Easier Testing**: Individual methods can be tested in isolation
5. **Reduced Duplication**: Common patterns extracted into helper methods
6. **Better Organization**: Logical grouping of related functionality
7. **Constants**: Magic strings replaced with named constants for better maintainability

This service is the core of the app monitoring system, handling detection, time management, blocking, and user interactions with a clean, maintainable architecture.
