import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  TextInput,
  Alert,
  Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { saveAddictiveApp, getAddictiveApps } from '../utils/storage';
import { InstalledApp, AddictiveApp } from '../types';

// Import the native module with error handling
// Using require() here is necessary for optional native modules that may not be linked
let RNInstalledApps: any = null;
try {
  // @ts-ignore - Native module may not be available
  RNInstalledApps = require('react-native-get-app-list');
} catch (e) {
  console.log('react-native-get-app-list not available:', e);
}


export default function AddAppScreen() {
  const router = useRouter();
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [filteredApps, setFilteredApps] = useState<InstalledApp[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [existingPackages, setExistingPackages] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);

  const loadApps = useCallback(async () => {
    try {
      // Get already added apps
      const addictiveApps = await getAddictiveApps();
      const packages = new Set(addictiveApps.map((a) => a.packageName));
      setExistingPackages(packages);

      // Try to get actual installed apps on Android
      if (Platform.OS === 'android' && RNInstalledApps?.getApps) {
        try {
          const installedApps = await RNInstalledApps.getApps();
          console.log('Loaded installed apps:', installedApps.length);
          
          // Transform to our format and filter out system apps
          const userApps: InstalledApp[] = installedApps
            .filter((app: any) => {
              // Filter out system apps and our own app
              const packageName = app.packageName || '';
              return !packageName.startsWith('com.android.') &&
                     !packageName.startsWith('com.google.android.') &&
                     packageName !== 'com.emergent.frontend' &&
                     app.appName && 
                     app.appName.trim() !== '';
            })
            .map((app: any) => ({
              packageName: app.packageName,
              appName: app.appName || app.packageName,
            }))
            .sort((a: InstalledApp, b: InstalledApp) => 
              a.appName.localeCompare(b.appName)
            );

          setApps(userApps);
          setFilteredApps(userApps);
        } catch (error) {
          console.error('Error loading installed apps:', error);
          // Fall back to common apps list
          loadCommonApps();
        }
      } else {
        // Not on Android or module not available, use common apps list
        loadCommonApps();
      }
    } catch (error) {
      console.error('Error loading apps:', error);
      loadCommonApps();
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadApps();
  }, [loadApps]);

  useEffect(() => {
    if (searchQuery) {
      const filtered = apps.filter((app) =>
        app.appName.toLowerCase().includes(searchQuery.toLowerCase()) ||
        app.packageName.toLowerCase().includes(searchQuery.toLowerCase())
      );
      setFilteredApps(filtered);
    } else {
      setFilteredApps(apps);
    }
  }, [searchQuery, apps]);

  const loadCommonApps = () => {
    // Fallback to common apps if native module is not available
    const commonApps: InstalledApp[] = [
      { packageName: 'com.instagram.android', appName: 'Instagram' },
      { packageName: 'com.facebook.katana', appName: 'Facebook' },
      { packageName: 'com.twitter.android', appName: 'Twitter' },
      { packageName: 'com.snapchat.android', appName: 'Snapchat' },
      { packageName: 'com.zhiliaoapp.musically', appName: 'TikTok' },
      { packageName: 'com.google.android.youtube', appName: 'YouTube' },
      { packageName: 'com.reddit.frontpage', appName: 'Reddit' },
      { packageName: 'com.whatsapp', appName: 'WhatsApp' },
      { packageName: 'com.spotify.music', appName: 'Spotify' },
      { packageName: 'com.netflix.mediaclient', appName: 'Netflix' },
      { packageName: 'com.amazon.mShop.android.shopping', appName: 'Amazon' },
      { packageName: 'com.linkedin.android', appName: 'LinkedIn' },
      { packageName: 'com.pinterest', appName: 'Pinterest' },
      { packageName: 'com.tinder', appName: 'Tinder' },
      { packageName: 'com.discord', appName: 'Discord' },
      { packageName: 'us.zoom.videomeetings', appName: 'Zoom' },
      { packageName: 'com.google.android.apps.messaging', appName: 'Messages' },
      { packageName: 'com.android.chrome', appName: 'Chrome' },
      { packageName: 'com.google.android.gm', appName: 'Gmail' },
      { packageName: 'com.microsoft.teams', appName: 'Microsoft Teams' },
    ];

    setApps(commonApps);
    setFilteredApps(commonApps);
    
    Alert.alert(
      'Note',
      'Running in fallback mode with common apps. Build with expo-dev-client to see actual installed apps.',
      [{ text: 'OK' }]
    );
  };

  const handleAddApp = async (app: InstalledApp) => {
    if (existingPackages.has(app.packageName)) {
      Alert.alert('Already Added', 'This app is already in your addictive apps list.');
      return;
    }

    try {
      const newApp: AddictiveApp = {
        packageName: app.packageName,
        appName: app.appName,
        behavior: 'ask',
        coolingPeriodMinutes: 30,
      };

      await saveAddictiveApp(newApp);
      Alert.alert('Success', `${app.appName} added to monitoring list`, [
        {
          text: 'OK',
          onPress: () => router.back(),
        },
      ]);
    } catch {
      Alert.alert('Error', 'Failed to add app. Please try again.');
    }
  };

  const renderApp = ({ item }: { item: InstalledApp }) => {
    const isAdded = existingPackages.has(item.packageName);

    return (
      <TouchableOpacity
        style={[styles.appCard, isAdded && styles.appCardDisabled]}
        onPress={() => !isAdded && handleAddApp(item)}
        disabled={isAdded}
      >
        <View style={styles.appIcon}>
          <Ionicons name="phone-portrait-outline" size={28} color={isAdded ? '#C7C7CC' : '#007AFF'} />
        </View>
        <View style={styles.appInfo}>
          <Text style={[styles.appName, isAdded && styles.textDisabled]}>{item.appName}</Text>
          <Text style={[styles.appPackage, isAdded && styles.textDisabled]}>{item.packageName}</Text>
        </View>
        {isAdded ? (
          <Ionicons name="checkmark-circle" size={24} color="#34C759" />
        ) : (
          <Ionicons name="add-circle" size={24} color="#007AFF" />
        )}
      </TouchableOpacity>
    );
  };

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.loadingText}>Loading apps...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.searchContainer}>
        <Ionicons name="search" size={20} color="#8E8E93" style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search apps..."
          value={searchQuery}
          onChangeText={setSearchQuery}
          placeholderTextColor="#8E8E93"
        />
      </View>

      <FlatList
        data={filteredApps}
        renderItem={renderApp}
        keyExtractor={(item) => item.packageName}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Ionicons name="search" size={48} color="#8E8E93" />
            <Text style={styles.emptyText}>No apps found</Text>
          </View>
        }
      />
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
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    margin: 16,
    paddingHorizontal: 12,
    borderRadius: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    height: 44,
    fontSize: 16,
    color: '#000',
  },
  list: {
    padding: 16,
    paddingTop: 0,
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
  appCardDisabled: {
    opacity: 0.6,
  },
  appIcon: {
    width: 48,
    height: 48,
    borderRadius: 10,
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
  },
  textDisabled: {
    color: '#C7C7CC',
  },
  loadingText: {
    fontSize: 16,
    color: '#8E8E93',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 48,
  },
  emptyText: {
    fontSize: 16,
    color: '#8E8E93',
    marginTop: 16,
  },
});
