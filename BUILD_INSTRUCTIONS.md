# Build Instructions for Phone Detox App

## Prerequisites

1. **Node.js** (v18 or higher)
2. **Yarn** package manager
3. **Android Studio** (for local builds)
4. **Expo account** (for EAS builds)

## Setup

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
yarn install
```

## Development (Expo Go - UI Only)

```bash
# Start development server
yarn start

# Scan QR code with Expo Go app
# Note: Native features (monitoring, overlays) won't work in Expo Go
```

## Production Build (Full Features)

### Option 1: EAS Build (Cloud Build - Easiest)

```bash
# Install EAS CLI globally
npm install -g eas-cli

# Login to your Expo account
eas login

# Configure EAS
eas build:configure

# Create development build
eas build --platform android --profile development

# Create production build
eas build --platform android --profile production
```

### Option 2: Local Build

```bash
# Generate native projects
npx expo prebuild --clean

# For development build
npx expo run:android

# For production APK
cd android
./gradlew assembleRelease
# APK will be in: android/app/build/outputs/apk/release/
```

## Native Module Integration

To enable full functionality, you need to add native modules:

### 1. Install Usage Stats Library

```bash
yarn add @brighthustle/react-native-usage-stats-manager
```

### 2. Install Overlay Library

```bash
yarn add react-native-draw-overlay
```

### 3. Rebuild Native Code

```bash
npx expo prebuild --clean
npx expo run:android
```

## Testing on Device

1. **Install the APK** on your Android device
2. **Grant Permissions**:
   - Settings → Apps → Phone Detox → Permissions
   - Enable "Usage Access"
   - Enable "Display over other apps"
3. **Disable Battery Optimization**:
   - Settings → Battery → Battery optimization
   - Find Phone Detox and select "Don't optimize"
4. **Test the App**:
   - Add apps to monitor
   - Configure settings
   - Open a monitored app
   - Time selection overlay should appear

## Common Issues

### Issue: Native modules not found
**Solution**: Run `npx expo prebuild --clean` after adding native dependencies

### Issue: Permissions not working
**Solution**: Check that app.plugin.js is properly configured in app.json

### Issue: Overlays not showing
**Solution**: Verify "Display over other apps" permission is granted

### Issue: Background monitoring not working
**Solution**: Disable battery optimization for the app

## Project Structure

```
phone-detox-app/
└── frontend/
    ├── app/                    # React Native screens
    │   ├── (tabs)/            # Tab navigation
    │   ├── _layout.tsx        # Root layout
    │   ├── add-app.tsx        # Add app screen
    │   └── app-settings.tsx   # Settings screen
    ├── types/                 # TypeScript types
    ├── utils/                 # Helper functions
    ├── app.json               # Expo configuration
    ├── app.plugin.js          # Android manifest plugin
    └── package.json           # Dependencies
```

## Next Steps

1. Build the app with native support
2. Test on a physical Android device
3. Implement native monitoring service
4. Add usage statistics tracking
5. Publish to Play Store (optional)

## Support

For issues or questions:
- Check the README.md file
- Review Expo documentation: https://docs.expo.dev
- Android developer docs: https://developer.android.com

---

Happy detoxing! 📱✨
