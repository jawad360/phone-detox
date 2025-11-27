import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { AddictiveApp } from '../types';
import { appMonitorManager } from '../utils/appMonitor';
import { getAddictiveApp, getAddictiveApps, getCoolingPeriod, saveAddictiveApp } from '../utils/storage';

const COOLING_PERIODS = [5, 10, 15, 30, 60, 120, 180];

export default function AppSettingsScreen() {
  const router = useRouter();
  const { packageName } = useLocalSearchParams<{ packageName: string }>();
  const [app, setApp] = useState<AddictiveApp | null>(null);
  const [loading, setLoading] = useState(true);
  const [isInCooling, setIsInCooling] = useState(false);
  const [activeSession, setActiveSession] = useState<{
    startTime: number;
    requestedMinutes: number;
  } | null>(null);

  const loadApp = useCallback(async () => {
    if (!packageName) return;

    try {
      const appData = await getAddictiveApp(packageName);
      if (appData) {
        setApp(appData);
        
        // Check if app is in cooling period
        const coolingEnd = await getCoolingPeriod(packageName);
        if (coolingEnd && coolingEnd > Date.now()) {
          setIsInCooling(true);
        }
      } else {
        Alert.alert('Error', 'App not found');
        router.back();
      }
    } catch {
      Alert.alert('Error', 'Failed to load app settings');
    } finally {
      setLoading(false);
    }
  }, [packageName, router]);

  const loadActiveSession = useCallback(async () => {
    if (!packageName) return;
    
    try {
      const session = await appMonitorManager.getActiveSession(packageName);
      if (session) {
        setActiveSession({
          startTime: session.startTime,
          requestedMinutes: session.requestedMinutes,
        });
      } else {
        setActiveSession(null);
      }
    } catch (error) {
      console.error('Error loading active session:', error);
    }
  }, [packageName]);

  useEffect(() => {
    loadApp();
    loadActiveSession();
    // Set up interval to update session info every second
    const interval = setInterval(() => {
      loadActiveSession();
    }, 1000);
    
    return () => clearInterval(interval);
  }, [packageName, loadActiveSession, loadApp]);


  const handleBehaviorChange = async (newBehavior: 'ask' | 'stop') => {
    if (!app || isInCooling) return;

    const updatedApp = { ...app, behavior: newBehavior };
    try {
      await saveAddictiveApp(updatedApp);
      setApp(updatedApp);
      // Update monitoring service
      const allApps = await getAddictiveApps();
      await appMonitorManager.updateMonitoredApps(allApps);
    } catch {
      Alert.alert('Error', 'Failed to update settings');
    }
  };

  const handleCoolingPeriodChange = async (minutes: number) => {
    if (!app || isInCooling) return;

    const updatedApp = { ...app, coolingPeriodMinutes: minutes };
    try {
      await saveAddictiveApp(updatedApp);
      setApp(updatedApp);
      // Update monitoring service
      const allApps = await getAddictiveApps();
      await appMonitorManager.updateMonitoredApps(allApps);
    } catch {
      Alert.alert('Error', 'Failed to update cooling period');
    }
  };

  const formatRemainingTime = (startTime: number, requestedMinutes: number): string => {
    const now = Date.now();
    const elapsedMs = now - startTime;
    const requestedMs = requestedMinutes * 60 * 1000;
    const remainingMs = requestedMs - elapsedMs;
    
    if (remainingMs <= 0) {
      return '0m';
    }
    
    const totalSeconds = Math.floor(remainingMs / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    } else if (minutes > 0) {
      return `${minutes}m ${seconds}s`;
    } else {
      return `${seconds}s`;
    }
  };

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.loadingText}>Loading...</Text>
      </View>
    );
  }

  if (!app) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.errorText}>App not found</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      {isInCooling && (
        <View style={styles.warningBanner}>
          <Ionicons name="warning" size={20} color="#FF9500" />
          <Text style={styles.warningText}>
            Settings locked during cooling period
          </Text>
        </View>
      )}

      <View style={styles.section}>
        <View style={styles.appHeader}>
          <View style={styles.appIcon}>
            <Ionicons name="phone-portrait-outline" size={40} color="#007AFF" />
          </View>
          <View style={styles.appInfo}>
            <Text style={styles.appName}>{app.appName}</Text>
            <Text style={styles.appPackage}>{app.packageName}</Text>
            {activeSession && (
              <View style={styles.sessionInfo}>
                <View style={styles.sessionRow}>
                  <Ionicons name="time-outline" size={16} color="#007AFF" />
                  <Text style={styles.sessionText}>
                    Requested: {activeSession.requestedMinutes >= 60 
                      ? `${Math.floor(activeSession.requestedMinutes / 60)}h ${activeSession.requestedMinutes % 60}m`
                      : `${activeSession.requestedMinutes}m`}
                  </Text>
                </View>
                <View style={styles.sessionRow}>
                  <Ionicons name="hourglass-outline" size={16} color="#FF9500" />
                  <Text style={styles.sessionText}>
                    Remaining: {formatRemainingTime(activeSession.startTime, activeSession.requestedMinutes)}
                  </Text>
                </View>
              </View>
            )}
          </View>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Timeout Behavior</Text>
        <Text style={styles.sectionDescription}>
          Choose what happens when your selected time runs out
        </Text>

        <TouchableOpacity
          style={[
            styles.optionCard,
            app.behavior === 'ask' && styles.optionCardSelected,
            isInCooling && styles.optionCardDisabled,
          ]}
          onPress={() => handleBehaviorChange('ask')}
          disabled={isInCooling}
        >
          <View style={styles.optionContent}>
            <View
              style={[
                styles.radio,
                app.behavior === 'ask' && styles.radioSelected,
              ]}
            >
              {app.behavior === 'ask' && (
                <View style={styles.radioInner} />
              )}
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>Ask for more time</Text>
              <Text style={styles.optionDescription}>
                Show a popup asking if you want to continue using the app
              </Text>
            </View>
          </View>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.optionCard,
            app.behavior === 'stop' && styles.optionCardSelected,
            isInCooling && styles.optionCardDisabled,
          ]}
          onPress={() => handleBehaviorChange('stop')}
          disabled={isInCooling}
        >
          <View style={styles.optionContent}>
            <View
              style={[
                styles.radio,
                app.behavior === 'stop' && styles.radioSelected,
              ]}
            >
              {app.behavior === 'stop' && (
                <View style={styles.radioInner} />
              )}
            </View>
            <View style={styles.optionInfo}>
              <Text style={styles.optionTitle}>Stop app immediately</Text>
              <Text style={styles.optionDescription}>
                Block the app and enter cooling period
              </Text>
            </View>
          </View>
        </TouchableOpacity>
      </View>

      {app.behavior === 'stop' && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Cooling Period</Text>
          <Text style={styles.sectionDescription}>
            How long should the app be blocked after stopping?
          </Text>

          <View style={styles.coolingGrid}>
            {COOLING_PERIODS.map((minutes) => (
              <TouchableOpacity
                key={minutes}
                style={[
                  styles.coolingOption,
                  app.coolingPeriodMinutes === minutes && styles.coolingOptionSelected,
                  isInCooling && styles.coolingOptionDisabled,
                ]}
                onPress={() => handleCoolingPeriodChange(minutes)}
                disabled={isInCooling}
              >
                <Text
                  style={[
                    styles.coolingText,
                    app.coolingPeriodMinutes === minutes && styles.coolingTextSelected,
                  ]}
                >
                  {minutes >= 60 ? `${minutes / 60}h` : `${minutes}m`}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      )}

      <View style={styles.infoSection}>
        <Ionicons name="information-circle" size={20} color="#007AFF" />
        <Text style={styles.infoText}>
          These settings control how the app behaves when you reach your time limit.
          During cooling periods, you cannot change these settings or open the app.
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
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
  },
  loadingText: {
    fontSize: 16,
    color: '#8E8E93',
  },
  errorText: {
    fontSize: 16,
    color: '#FF3B30',
  },
  warningBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFF3CD',
    padding: 12,
    marginBottom: 16,
  },
  warningText: {
    fontSize: 14,
    color: '#856404',
    marginLeft: 8,
    flex: 1,
  },
  section: {
    padding: 16,
  },
  appHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
  },
  appIcon: {
    width: 64,
    height: 64,
    borderRadius: 12,
    backgroundColor: '#F2F2F7',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  appInfo: {
    flex: 1,
  },
  appName: {
    fontSize: 20,
    fontWeight: '700',
    color: '#000',
    marginBottom: 4,
  },
  appPackage: {
    fontSize: 12,
    color: '#8E8E93',
    marginBottom: 8,
  },
  sessionInfo: {
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#E5E5EA',
  },
  sessionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  sessionText: {
    fontSize: 13,
    color: '#007AFF',
    marginLeft: 6,
    fontWeight: '500',
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#000',
    marginBottom: 8,
  },
  sectionDescription: {
    fontSize: 14,
    color: '#8E8E93',
    marginBottom: 16,
  },
  optionCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 2,
    borderColor: '#E5E5EA',
  },
  optionCardSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#F0F8FF',
  },
  optionCardDisabled: {
    opacity: 0.5,
  },
  optionContent: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  radio: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: '#C7C7CC',
    marginRight: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  radioSelected: {
    borderColor: '#007AFF',
  },
  radioInner: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#007AFF',
  },
  optionInfo: {
    flex: 1,
  },
  optionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 4,
  },
  optionDescription: {
    fontSize: 13,
    color: '#8E8E93',
  },
  coolingGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: -4,
  },
  coolingOption: {
    width: '23%',
    aspectRatio: 1,
    margin: '1%',
    backgroundColor: '#fff',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#E5E5EA',
  },
  coolingOptionSelected: {
    borderColor: '#007AFF',
    backgroundColor: '#F0F8FF',
  },
  coolingOptionDisabled: {
    opacity: 0.5,
  },
  coolingText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
  },
  coolingTextSelected: {
    color: '#007AFF',
  },
  infoSection: {
    flexDirection: 'row',
    margin: 16,
    padding: 16,
    backgroundColor: '#E8F4FD',
    borderRadius: 12,
  },
  infoText: {
    flex: 1,
    fontSize: 13,
    color: '#007AFF',
    marginLeft: 8,
    lineHeight: 18,
  },
});
