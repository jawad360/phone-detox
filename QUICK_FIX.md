# Quick Fix: Usage Access Toggle Disabled

## The Problem
The "Permit access to app usage data" toggle is disabled/greyed out.

## Quick Solution

Run these commands in order:

```bash
# 1. Stop the metro bundler if running (Ctrl+C)

# 2. Navigate to project directory
cd /Users/jawadsonalkar/Personal/downloads/detox/phone-detox-app/phone-detox

# 3. Completely uninstall the app from device
adb uninstall com.techmania.phonedetox

# 4. Clean and rebuild (this regenerates AndroidManifest with fixes)
npx expo prebuild --clean

# 5. Build and install fresh copy
npx expo run:android
```

## What This Does

1. **Removes the old app** with its broken permission state
2. **Regenerates native code** with updated manifest (includes tools namespace fix)
3. **Builds fresh APK** with proper signing
4. **Installs on device** as a new installation

## After Installation

1. Open the app
2. Navigate through permissions screen
3. Tap "Usage Access"
4. The toggle should now be **ENABLED** (not greyed out)
5. Turn it **ON**

## If Still Disabled

Try clearing the permission from settings first:

```bash
# Clear permission state
adb shell pm clear com.techmania.phonedetox

# Then reinstall
npx expo run:android
```

## Why This Happens

- Different build signatures (debug vs release)
- Permission state corruption
- Previous incomplete installation

## Prevention

Always use `npx expo prebuild --clean` before building when making manifest changes.

