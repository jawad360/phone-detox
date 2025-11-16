import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useRouter } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  FlatList,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { AddictiveApp } from '../../types';
import { appMonitorManager } from '../../utils/appMonitor';
import { getAddictiveApps, removeAddictiveApp } from '../../utils/storage';

export default function AppsScreen() {
  const router = useRouter();
  const [apps, setApps] = useState<AddictiveApp[]>([]);
  const [loading, setLoading] = useState(true);

  const loadApps = useCallback(async () => {
    try {
      const savedApps = await getAddictiveApps();
      setApps(savedApps);
    } catch (error) {
      console.error('Error loading apps:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  // Reload apps whenever the screen comes into focus
  useFocusEffect(
    useCallback(() => {
      loadApps();
    }, [loadApps])
  );

  // Initialize app monitoring when apps change
  useEffect(() => {
    const initializeMonitoring = async () => {
      if (apps.length > 0) {
        await appMonitorManager.updateMonitoredApps(apps);
      }
    };
    initializeMonitoring();
  }, [apps]);

  // Initialize monitoring on mount
  useEffect(() => {
    appMonitorManager.initialize();
  }, []);

  const handleRemoveApp = async (packageName: string) => {
    Alert.alert(
      'Remove App',
      'Are you sure you want to remove this app from monitoring?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: async () => {
            await removeAddictiveApp(packageName);
            await loadApps();
            // Update monitoring service
            const updatedApps = await getAddictiveApps();
            await appMonitorManager.updateMonitoredApps(updatedApps);
          },
        },
      ]
    );
  };

  const handleAppPress = (app: AddictiveApp) => {
    router.push({
      pathname: '/app-settings',
      params: { packageName: app.packageName },
    });
  };

  const renderApp = ({ item }: { item: AddictiveApp }) => (
    <TouchableOpacity
      style={styles.appCard}
      onPress={() => handleAppPress(item)}
    >
      <View style={styles.appIcon}>
        <Ionicons name="phone-portrait-outline" size={32} color="#007AFF" />
      </View>
      <View style={styles.appInfo}>
        <Text style={styles.appName}>{item.appName}</Text>
        <Text style={styles.appPackage}>{item.packageName}</Text>
        <View style={styles.settingsRow}>
          <Text style={styles.settingText}>
            {item.behavior === 'stop' ? 'Auto-stop' : 'Ask for more time'}
          </Text>
          {item.behavior === 'stop' && (
            <Text style={styles.coolingText}>
              Cooling: {item.coolingPeriodMinutes}min
            </Text>
          )}
        </View>
      </View>
      <TouchableOpacity
        style={styles.removeButton}
        onPress={() => handleRemoveApp(item.packageName)}
      >
        <Ionicons name="trash-outline" size={20} color="#FF3B30" />
      </TouchableOpacity>
    </TouchableOpacity>
  );

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.loadingText}>Loading...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Addictive Apps</Text>
        <TouchableOpacity
          style={styles.addButton}
          onPress={() => router.push('/add-app')}
        >
          <Ionicons name="add" size={24} color="#fff" />
        </TouchableOpacity>
      </View>

      {apps.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Ionicons name="apps-outline" size={64} color="#8E8E93" />
          <Text style={styles.emptyText}>No addictive apps added yet</Text>
          <Text style={styles.emptySubtext}>
            Tap the + button to add apps to monitor
          </Text>
        </View>
      ) : (
        <FlatList
          data={apps}
          renderItem={renderApp}
          keyExtractor={(item) => item.packageName}
          contentContainerStyle={styles.list}
        />
      )}
    </View>
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
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
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
  addButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#007AFF',
    justifyContent: 'center',
    alignItems: 'center',
  },
  list: {
    padding: 16,
  },
  appCard: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  appIcon: {
    width: 56,
    height: 56,
    borderRadius: 12,
    backgroundColor: '#F2F2F7',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  appInfo: {
    flex: 1,
  },
  appName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 4,
  },
  appPackage: {
    fontSize: 12,
    color: '#8E8E93',
    marginBottom: 6,
  },
  settingsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  settingText: {
    fontSize: 12,
    color: '#007AFF',
    fontWeight: '500',
  },
  coolingText: {
    fontSize: 12,
    color: '#FF9500',
    fontWeight: '500',
    marginLeft: 8,
  },
  removeButton: {
    padding: 8,
  },
  loadingText: {
    fontSize: 16,
    color: '#8E8E93',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
    marginTop: 16,
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#8E8E93',
    textAlign: 'center',
  },
});
