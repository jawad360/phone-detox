import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Platform,
  Alert,
  Linking,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as IntentLauncher from 'expo-intent-launcher';
import Constants from 'expo-constants';

export default function SettingsScreen() {
  const getPackageName = () => {
    return Constants.expoConfig?.android?.package || 'com.techmania.phonedetox';
  };

  const openUsageSettings = async () => {
    if (Platform.OS === 'android') {
      try {
        const packageName = getPackageName();
        await IntentLauncher.startActivityAsync(
          IntentLauncher.ActivityAction.USAGE_ACCESS_SETTINGS,
          {
            data: `package:${packageName}`,
          }
        );
      } catch (error) {
        console.error('Error opening usage stats settings:', error);
        // Fallback to general settings
        try {
          await IntentLauncher.startActivityAsync(
            IntentLauncher.ActivityAction.USAGE_ACCESS_SETTINGS
          );
        } catch {
          Alert.alert('Error', 'Could not open usage access settings');
        }
      }
    }
  };

  const openOverlaySettings = async () => {
    if (Platform.OS === 'android') {
      try {
        const packageName = getPackageName();
        await IntentLauncher.startActivityAsync(
          IntentLauncher.ActivityAction.MANAGE_OVERLAY_PERMISSION,
          {
            data: `package:${packageName}`,
          }
        );
      } catch {
        Alert.alert('Error', 'Could not open overlay settings');
      }
    }
  };

  const openBatterySettings = async () => {
    if (Platform.OS === 'android') {
      try {
        // Open Battery Optimization Settings list (most compatible)
        await IntentLauncher.startActivityAsync(
          'android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS'
        );
        Alert.alert(
          'Battery Optimization',
          'Please find "Phone Detox" in the list and select "Don\'t optimize" or "Not optimized".',
          [{ text: 'OK' }]
        );
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
        } catch {
          Alert.alert(
            'Manual Setup Required',
            'Please manually disable battery optimization:\n\n1. Go to Settings → Apps\n2. Find "Phone Detox"\n3. Tap Battery → Battery optimization\n4. Select "Don\'t optimize"',
            [{ text: 'OK' }]
          );
        }
      }
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Settings</Text>
      </View>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Permissions</Text>
          <TouchableOpacity style={styles.settingCard} onPress={openUsageSettings}>
            <View style={styles.settingIcon}>
              <Ionicons name="stats-chart" size={24} color="#007AFF" />
            </View>
            <View style={styles.settingInfo}>
              <Text style={styles.settingTitle}>Usage Access</Text>
              <Text style={styles.settingSubtitle}>
                Required to monitor app usage
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color="#C7C7CC" />
          </TouchableOpacity>

          <TouchableOpacity style={styles.settingCard} onPress={openOverlaySettings}>
            <View style={styles.settingIcon}>
              <Ionicons name="layers" size={24} color="#007AFF" />
            </View>
            <View style={styles.settingInfo}>
              <Text style={styles.settingTitle}>Display Over Other Apps</Text>
              <Text style={styles.settingSubtitle}>
                Required to show time selection popups
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color="#C7C7CC" />
          </TouchableOpacity>

          <TouchableOpacity style={styles.settingCard} onPress={openBatterySettings}>
            <View style={styles.settingIcon}>
              <Ionicons name="battery-charging" size={24} color="#FF9500" />
            </View>
            <View style={styles.settingInfo}>
              <Text style={styles.settingTitle}>Battery Optimization</Text>
              <Text style={styles.settingSubtitle}>
                Disable to ensure app works in background
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color="#C7C7CC" />
          </TouchableOpacity>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>About</Text>
          <View style={styles.infoCard}>
            <Text style={styles.infoText}>
              Phone Detox helps you reduce phone usage by monitoring and limiting
              time spent on addictive apps.
            </Text>
          </View>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  header: {
    padding: 16,
    paddingTop: Platform.OS === 'ios' ? 60 : 40,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#000',
  },
  content: {
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: '#8E8E93',
    textTransform: 'uppercase',
    marginBottom: 8,
    marginLeft: 4,
  },
  settingCard: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 8,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  settingIcon: {
    width: 40,
    height: 40,
    borderRadius: 8,
    backgroundColor: '#F2F2F7',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  settingInfo: {
    flex: 1,
  },
  settingTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 4,
  },
  settingSubtitle: {
    fontSize: 13,
    color: '#8E8E93',
  },
  infoCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  infoText: {
    fontSize: 14,
    color: '#000',
    lineHeight: 20,
  },
});
