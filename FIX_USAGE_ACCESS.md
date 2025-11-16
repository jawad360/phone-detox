# Fix: Phone Detox App Not Showing in Usage Access Settings

## Problem
The Phone Detox app was not appearing in the Usage Access list when users tried to grant permissions. This happened because the app was opening the general settings page instead of the app-specific settings page.

## Solution
Updated the permission request methods to include the app's package name using the `data` parameter with `package:` URI scheme. This directly opens the settings page for the specific app.

## What Changed

### 1. Permissions Screen (`app/permissions.tsx`)
- Added `Constants` import from `expo-constants`
- Added `getPackageName()` helper function
- Updated all permission request methods to include package-specific data:

```javascript
// Before
await IntentLauncher.startActivityAsync(
  IntentLauncher.ActivityAction.USAGE_ACCESS_SETTINGS
);

// After
const packageName = getPackageName();
await IntentLauncher.startActivityAsync(
  IntentLauncher.ActivityAction.USAGE_ACCESS_SETTINGS,
  {
    data: `package:${packageName}`,
  }
);
```

### 2. Settings Screen (`app/(tabs)/settings.tsx`)
- Added same improvements as permissions screen
- Now all three permission settings open directly to the app's specific page

## How It Works Now

### Usage Access Permission
- Opens directly to the Phone Detox app in the Usage Access list
- User can immediately toggle the switch for your app
- Fallback to general settings if package-specific intent fails

### Display Over Other Apps
- Opens directly to the Phone Detox app permission toggle
- User sees the specific app's permission switch

### Battery Optimization
- Attempts to open app-specific battery optimization settings
- Falls back to general settings with an alert guiding the user
- This one may not work on all Android versions due to API differences

## Package Name Resolution

The app uses `expo-constants` to get the package name from your `app.json`:

```javascript
const getPackageName = () => {
  return Constants.expoConfig?.android?.package || 'com.techmania.phonedetox';
};
```

This reads from:
- `app.json` → `expo.android.package` = `com.techmania.phonedetox`

## Testing

1. **Clear app data or reinstall** to trigger the permissions screen
2. Tap "Grant Permissions" button
3. **Verify**: The Usage Access settings should show your app highlighted or at the top
4. Toggle the permission switch
5. Repeat for other permissions

## Important Notes

### Android Version Differences
- Some permission intents work differently across Android versions
- The code includes fallbacks for compatibility
- Battery optimization intent may not work on all devices (hence the fallback to general settings)

### If the App Still Doesn't Appear
If the app still doesn't show in Usage Access after these changes:

1. **Check AndroidManifest.xml** permissions:
   ```xml
   <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
   ```

2. **Rebuild the app**:
   ```bash
   npx expo prebuild --clean
   npx expo run:android
   ```

3. **Manually navigate** to settings:
   - Settings → Apps → Special access → Usage access
   - Find "Phone Detox" in the list
   - Toggle it on

4. **Check package name** matches your build:
   - The package in `app.json` must match the actual built APK
   - Check with: `adb shell pm list packages | grep phone`

## URI Scheme Format

The key fix was using the proper Android URI scheme:
```
package:<your.package.name>
```

This tells Android to:
1. Open the specific settings activity
2. Navigate directly to the app with that package name
3. Highlight or focus on that app in the list

## Future Improvements

1. **Permission Status Check**: Add runtime verification that permissions were actually granted
2. **Better Error Handling**: Provide more specific guidance based on Android version
3. **Alternative Permission Request**: Use native Android permission request dialogs where possible
4. **Permission Verification Screen**: Show checkmarks or warnings based on actual permission status

## Related Files
- `app/permissions.tsx` - Initial permission request screen
- `app/(tabs)/settings.tsx` - Settings screen to revisit permissions
- `app.json` - Package name configuration
- `app.plugin.js` - Android manifest modifications

