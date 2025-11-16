import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import * as IntentLauncher from 'expo-intent-launcher';
import { useRouter } from 'expo-router';
import React, { useState } from 'react';
import {
  Alert,
  Linking,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

const PERMISSIONS_GRANTED_KEY = '@permissions_granted';

export default function PermissionsScreen() {
  const router = useRouter();
  const [requesting, setRequesting] = useState(false);

  const getPackageName = () => {
    return Constants.expoConfig?.android?.package || 'com.techmania.phonedetox';
  };

  const requestUsageStatsPermission = async () => {
    try {
      if (Platform.OS === 'android') {
        const packageName = getPackageName();
        // Open Usage Access Settings directly to the app
        await IntentLauncher.startActivityAsync(
          IntentLauncher.ActivityAction.USAGE_ACCESS_SETTINGS,
          {
            data: `package:${packageName}`,
          }
        );
      }
    } catch (error) {
      console.error('Error opening usage stats settings:', error);
      // Fallback to general settings
      try {
        await IntentLauncher.startActivityAsync(
          IntentLauncher.ActivityAction.USAGE_ACCESS_SETTINGS
        );
      } catch (fallbackError) {
        Alert.alert(
          'Error',
          'Could not open settings. Please manually enable Usage Access for this app in Settings > Apps > Special access > Usage access.'
        );
      }
    }
  };

  const requestOverlayPermission = async () => {
    try {
      if (Platform.OS === 'android') {
        const packageName = getPackageName();
        // Open Overlay Settings with package URI
        await IntentLauncher.startActivityAsync(
          IntentLauncher.ActivityAction.MANAGE_OVERLAY_PERMISSION,
          {
            data: `package:${packageName}`,
          }
        );
      }
    } catch (error) {
      console.error('Error opening overlay settings:', error);
      Alert.alert(
        'Error',
        'Could not open settings. Please manually enable Display over other apps for this app in Settings > Apps > Special access.'
      );
    }
  };

  const requestBatteryOptimization = async () => {
    try {
      if (Platform.OS === 'android') {
        // Open Battery Optimization Settings list (most compatible)
        await IntentLauncher.startActivityAsync(
          'android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS'
        );
        Alert.alert(
          'Battery Optimization',
          'Please find "Phone Detox" in the list and select "Don\'t optimize" or "Not optimized".',
          [{ text: 'OK' }]
        );
      }
    } catch (error) {
      console.error('Error opening battery optimization settings:', error);
      // Fallback to app settings
      try {
        await Linking.openSettings();
        Alert.alert(
          'Battery Settings',
          'Please navigate to Battery → Battery optimization and disable it for Phone Detox.',
          [{ text: 'OK' }]
        );
      } catch (fallbackError) {
        console.error('Fallback also failed:', fallbackError);
        Alert.alert(
          'Manual Setup Required',
          'Please manually disable battery optimization:\n\n1. Go to Settings → Apps\n2. Find "Phone Detox"\n3. Tap Battery → Battery optimization\n4. Select "Don\'t optimize"',
          [{ text: 'OK' }]
        );
      }
    }
  };

  const handleContinue = async () => {
    setRequesting(true);
    
    Alert.alert(
      'Important Permissions',
      'This app requires special permissions to monitor app usage and provide notifications. You will be redirected to settings to grant these permissions.\n\n1. Usage Access\n2. Display over other apps\n3. Battery optimization (optional but recommended)',
      [
        { text: 'Cancel', style: 'cancel', onPress: () => setRequesting(false) },
        {
          text: 'Continue',
          onPress: async () => {
            // Request permissions sequentially
            await requestUsageStatsPermission();
            
            // Give user time to grant first permission
            setTimeout(async () => {
              await requestOverlayPermission();
              
              setTimeout(async () => {
                await requestBatteryOptimization();
                
                // Mark permissions as requested
                await AsyncStorage.setItem(PERMISSIONS_GRANTED_KEY, 'true');
                setRequesting(false);
                
                // Navigate to main app
                router.replace('/(tabs)');
              }, 1000);
            }, 1000);
          },
        },
      ]
    );
  };

  const handleSkip = async () => {
    Alert.alert(
      'Skip Permissions?',
      'The app may not function correctly without these permissions. You can grant them later in Settings.',
      [
        { text: 'Go Back', style: 'cancel' },
        {
          text: 'Skip Anyway',
          style: 'destructive',
          onPress: async () => {
            await AsyncStorage.setItem(PERMISSIONS_GRANTED_KEY, 'skipped');
            router.replace('/(tabs)');
          },
        },
      ]
    );
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.iconContainer}>
        <Ionicons name="shield-checkmark" size={80} color="#007AFF" />
      </View>

      <Text style={styles.title}>Permissions Required</Text>
      <Text style={styles.subtitle}>
        To monitor and control app usage, we need your permission
      </Text>

      <View style={styles.permissionsContainer}>
        <View style={styles.permissionCard}>
          <View style={styles.permissionIcon}>
            <Ionicons name="time-outline" size={32} color="#007AFF" />
          </View>
          <View style={styles.permissionInfo}>
            <Text style={styles.permissionTitle}>Usage Access</Text>
            <Text style={styles.permissionDescription}>
              Required to monitor which apps you open and track usage time
            </Text>
          </View>
        </View>

        <View style={styles.permissionCard}>
          <View style={styles.permissionIcon}>
            <Ionicons name="notifications-outline" size={32} color="#007AFF" />
          </View>
          <View style={styles.permissionInfo}>
            <Text style={styles.permissionTitle}>Display Over Other Apps</Text>
            <Text style={styles.permissionDescription}>
              Required to show prompts when you try to open monitored apps
            </Text>
          </View>
        </View>

        <View style={styles.permissionCard}>
          <View style={styles.permissionIcon}>
            <Ionicons name="battery-charging-outline" size={32} color="#FF9500" />
          </View>
          <View style={styles.permissionInfo}>
            <Text style={styles.permissionTitle}>Battery Optimization</Text>
            <Text style={styles.permissionDescription}>
              Recommended to ensure the app works in the background
            </Text>
            <Text style={styles.optionalBadge}>Optional</Text>
          </View>
        </View>
      </View>

      <View style={styles.buttonContainer}>
        <TouchableOpacity
          style={styles.continueButton}
          onPress={handleContinue}
          disabled={requesting}
        >
          <Text style={styles.continueButtonText}>
            {requesting ? 'Requesting...' : 'Grant Permissions'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.skipButton}
          onPress={handleSkip}
          disabled={requesting}
        >
          <Text style={styles.skipButtonText}>Skip for Now</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.infoContainer}>
        <Ionicons name="information-circle-outline" size={20} color="#8E8E93" />
        <Text style={styles.infoText}>
          Your privacy is important. We only use these permissions to provide the app&apos;s core functionality.
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  content: {
    padding: 24,
    paddingTop: 60,
  },
  iconContainer: {
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#000',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#8E8E93',
    textAlign: 'center',
    marginBottom: 32,
    lineHeight: 22,
  },
  permissionsContainer: {
    marginBottom: 32,
  },
  permissionCard: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  permissionIcon: {
    width: 56,
    height: 56,
    borderRadius: 12,
    backgroundColor: '#F2F2F7',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  permissionInfo: {
    flex: 1,
    justifyContent: 'center',
  },
  permissionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 4,
  },
  permissionDescription: {
    fontSize: 13,
    color: '#8E8E93',
    lineHeight: 18,
  },
  optionalBadge: {
    fontSize: 11,
    color: '#FF9500',
    fontWeight: '600',
    marginTop: 4,
  },
  buttonContainer: {
    marginBottom: 24,
  },
  continueButton: {
    backgroundColor: '#007AFF',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    marginBottom: 12,
  },
  continueButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
  skipButton: {
    backgroundColor: 'transparent',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  skipButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#8E8E93',
  },
  infoContainer: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
  },
  infoText: {
    flex: 1,
    fontSize: 13,
    color: '#8E8E93',
    lineHeight: 18,
    marginLeft: 12,
  },
});

