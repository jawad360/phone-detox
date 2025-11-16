# Troubleshooting: Usage Access Toggle Disabled

## The Problem

The "Permit access to app usage data" toggle in Usage Access settings is **greyed out/disabled**, preventing users from granting the permission.

## Why This Happens

The `PACKAGE_USAGE_STATS` permission toggle can be disabled for several reasons:

### 1. **App Not Signed with Same Key** (Most Common)
- If you're rebuilding the app, Android sees it as a "different" app
- The old permission grant is invalidated
- The toggle becomes disabled until the old app data is cleared

### 2. **Development vs Release Build Mismatch**
- Debug builds and release builds have different signatures
- Switching between them causes the toggle to be disabled

### 3. **Android Security Restrictions**
- Some Android versions (especially custom ROMs) restrict this permission
- Enterprise/MDM policies might disable it

### 4. **App Already Has Permission (But Revoked)**
- Sometimes the system still thinks the app has permission
- But it's actually revoked, causing the toggle to be stuck

## Solutions

### Solution 1: Clear App Data and Reinstall (Recommended)

1. **Uninstall the app completely**:
   ```bash
   adb uninstall com.techmania.phonedetox
   ```

2. **Clear Usage Access Settings**:
   - Go to Settings → Apps → Special access → Usage access
   - If "Phone Detox" appears, tap it and revoke any existing permission
   - Go back to main Settings

3. **Rebuild and Install**:
   ```bash
   cd /Users/jawadsonalkar/Personal/downloads/detox/phone-detox-app/phone-detox
   npx expo prebuild --clean
   npx expo run:android
   ```

4. **Grant Permission Again**:
   - The toggle should now be enabled
   - Turn it ON for Phone Detox

### Solution 2: Use ADB to Reset Permission

```bash
# Revoke the permission (if stuck)
adb shell pm revoke com.techmania.phonedetox android.permission.PACKAGE_USAGE_STATS

# Clear package restrictions
adb shell pm clear com.techmania.phonedetox

# Restart the device
adb reboot
```

### Solution 3: Reset App Permissions Manually

1. **Open Settings → Apps**
2. **Find "Phone Detox"**
3. **Tap "Storage & cache"**
4. **Tap "Clear storage"**  (⚠️ This will delete all app data)
5. **Go back and tap "Permissions"**
6. **Reset all permissions**
7. **Reopen the app**
8. **Try granting Usage Access again**

### Solution 4: Check Package Name Consistency

Make sure your `app.json` package name matches the built APK:

```json
{
  "expo": {
    "android": {
      "package": "com.techmania.phonedetox"
    }
  }
}
```

Verify the installed package:
```bash
adb shell pm list packages | grep phone
```

Should show: `package:com.techmania.phonedetox`

### Solution 5: Check if Emulator/Device Supports It

Some emulators and devices don't fully support Usage Access:

1. **Try on a real device** instead of emulator
2. **Use Android 10+** (API 29+) for best compatibility
3. **Avoid heavily customized ROMs** (some disable this feature)

## After Fixing

Once the toggle is enabled and you've granted permission:

1. ✅ Toggle should be **ON** and **blue/green**
2. ✅ App should appear in the "Permitted" list
3. ✅ No error messages when opening the app
4. ✅ App can now read usage statistics

## Verification

Test if the permission is granted:

```bash
# Check permission status
adb shell dumpsys usagestats | grep -A 20 "com.techmania.phonedetox"
```

If permission is granted, you'll see usage data. If not, it will be empty or show an error.

## Common Mistakes

❌ **Don't just force-stop the app** - This doesn't clear the permission state  
❌ **Don't switch between debug and release builds** - They have different signatures  
❌ **Don't use "Clear cache"** - Use "Clear storage" instead  
✅ **Do a clean uninstall + reinstall** - Most reliable method  
✅ **Do check device compatibility** - Test on real device if possible  

## Prevention

To avoid this issue in the future:

1. **Use consistent signing** for all builds (debug and release)
2. **Don't change package name** after initial release
3. **Document your keystore** and keep it safe
4. **Test on multiple devices** before release
5. **Use EAS Build** for consistent builds

## Additional Notes

### For Development
- The issue is more common during development
- Each rebuild with different signature causes it
- Use `expo-dev-client` builds for consistency

### For Production
- Release builds should use same keystore always
- Users won't face this unless they downgrade/sideload
- Play Store updates maintain the signature

## Still Not Working?

If none of these solutions work:

1. **Check logcat** for permission errors:
   ```bash
   adb logcat | grep -i "permission\|usage"
   ```

2. **Verify Android version**:
   ```bash
   adb shell getprop ro.build.version.sdk
   ```
   Should be 21+ (Android 5.0+)

3. **Check device restrictions**:
   - Some manufacturers (Xiaomi, Huawei) have extra restrictions
   - Look for "Autostart" or "Background restrictions" settings

4. **Try a different device/emulator** to rule out device-specific issues

## Summary

The disabled toggle is usually caused by signature mismatches during development. A clean uninstall and reinstall typically fixes it. For production, ensure consistent signing and the issue should not occur.

