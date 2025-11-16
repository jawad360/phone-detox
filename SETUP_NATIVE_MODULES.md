# Setting Up Native Modules for Phone Detox App

## The Issue

The error "The package 'react-native-get-app-list' doesn't seem to be linked" occurs because you're trying to use a native module that requires a development build. This feature **does not work in Expo Go**.

## Solution Options

### Option 1: Build with Expo Development Client (Recommended)

1. **Install Android Studio** (if not already installed)
   - Download from: https://developer.android.com/studio
   - During installation, make sure to install Android SDK

2. **Set up Android SDK environment variables**

   Add to your `~/.zshrc` or `~/.bash_profile`:
   ```bash
   export ANDROID_HOME=$HOME/Library/Android/sdk
   export PATH=$PATH:$ANDROID_HOME/emulator
   export PATH=$PATH:$ANDROID_HOME/platform-tools
   export PATH=$PATH:$ANDROID_HOME/tools
   export PATH=$PATH:$ANDROID_HOME/tools/bin
   ```

   Then run:
   ```bash
   source ~/.zshrc  # or source ~/.bash_profile
   ```

3. **Create local.properties file**

   The prebuild already created the android folder. Now create:
   ```bash
   echo "sdk.dir=$HOME/Library/Android/sdk" > android/local.properties
   ```

4. **Build and run the development version**
   ```bash
   npx expo run:android
   ```

   This will:
   - Build a development version with all native modules properly linked
   - Install it on your connected device or emulator
   - The `react-native-get-app-list` module will work correctly

### Option 2: Use EAS Build (Cloud Build)

If you don't want to set up Android Studio locally:

1. **Install EAS CLI**
   ```bash
   npm install -g eas-cli
   ```

2. **Login to Expo**
   ```bash
   eas login
   ```

3. **Build development version**
   ```bash
   eas build --profile development --platform android
   ```

4. **Install the APK** from the EAS dashboard on your device

### Option 3: Continue with Fallback Mode (Current Setup)

The app will work with a predefined list of common apps. When you try to add an app, you'll see a message explaining that you need a development build to see actual installed apps.

## Current Status

✅ The code has been updated to:
- Gracefully handle the missing native module
- Show a fallback list of common apps
- Display a helpful message when the native module isn't available

✅ `expo prebuild` has been run:
- Android and iOS native folders have been generated
- The native module is configured and ready to link

❌ Android SDK not configured:
- Need to set ANDROID_HOME environment variable
- Need to create android/local.properties file

## Testing

After building with one of the options above:

1. Open the app
2. Navigate to "Add App" screen
3. You should see your actual installed apps instead of just the fallback list
4. The native module will be working properly

## Notes

- Expo Go cannot run native modules that aren't included in its runtime
- Development builds include all your native modules
- After setting up once, subsequent builds will be faster
- The app will still work in fallback mode without the development build

