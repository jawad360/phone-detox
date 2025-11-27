import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import { AddictiveApp } from '../types';
import { getAddictiveApps, saveUsageSession, setCoolingPeriod } from './storage';

const { AppMonitor } = NativeModules;

export interface AppMonitorEvents {
  sessionStarted: (data: {
    packageName: string;
    startTime: number;
    requestedMinutes: number;
    behavior: string;
  }) => void;
  sessionExtended: (data: {
    packageName: string;
    startTime: number;
    requestedMinutes: number;
    behavior: string;
  }) => void;
  sessionExpired: (data: {
    packageName: string;
    startTime: number;
    requestedMinutes: number;
    behavior: string;
  }) => void;
  coolingPeriodStarted: (data: {
    packageName: string;
    coolingEndTime: number;
    coolingMinutes: number;
  }) => void;
  setCoolingPeriod: (data: {
    packageName: string;
    endTime: number;
  }) => void;
}

class AppMonitorManager {
  private eventEmitter: NativeEventEmitter | null = null;
  private isInitialized = false;

  constructor() {
    if (Platform.OS === 'android' && AppMonitor) {
      this.eventEmitter = new NativeEventEmitter(AppMonitor);
      this.setupEventListeners();
    }
  }

  private setupEventListeners() {
    if (!this.eventEmitter) return;

    this.eventEmitter.addListener('sessionStarted', async (data) => {
      await saveUsageSession({
        packageName: data.packageName,
        startTime: data.startTime,
        requestedMinutes: data.requestedMinutes,
      });
    });

    this.eventEmitter.addListener('sessionExtended', async (data) => {
      await saveUsageSession({
        packageName: data.packageName,
        startTime: data.startTime,
        requestedMinutes: data.requestedMinutes,
      });
    });

    this.eventEmitter.addListener('setCoolingPeriod', async (data) => {
      await setCoolingPeriod(data.packageName, data.endTime);
    });
  }

  async initialize() {
    if (Platform.OS !== 'android' || !AppMonitor || this.isInitialized) {
      return;
    }

    try {
      // Load monitored apps and start monitoring
      const apps = await getAddictiveApps();
      await this.updateMonitoredApps(apps);
      AppMonitor.startMonitoring();
      this.isInitialized = true;
    } catch (error) {
      console.error('Error initializing app monitor:', error);
    }
  }

  async updateMonitoredApps(apps: AddictiveApp[]) {
    if (Platform.OS !== 'android' || !AppMonitor) {
      return;
    }

    try {
      // Convert apps to format expected by native module
      const appData = apps.map((app) => ({
        packageName: app.packageName,
        behavior: app.behavior,
        coolingPeriodMinutes: app.coolingPeriodMinutes,
      }));

      // Update app configs in native module
      for (const app of apps) {
        AppMonitor.updateAppConfig(app.packageName, {
          behavior: app.behavior,
          coolingPeriodMinutes: app.coolingPeriodMinutes,
        });
      }

      // Update monitored apps list
      AppMonitor.updateMonitoredApps(appData);
    } catch (error) {
      console.error('Error updating monitored apps:', error);
    }
  }

  stopMonitoring() {
    if (Platform.OS === 'android' && AppMonitor) {
      AppMonitor.stopMonitoring();
      this.isInitialized = false;
    }
  }

  async getActiveSession(packageName: string): Promise<{
    packageName: string;
    startTime: number;
    requestedMinutes: number;
    behavior: string;
  } | null> {
    if (Platform.OS !== 'android' || !AppMonitor) {
      return null;
    }

    try {
      const session = await AppMonitor.getActiveSession(packageName);
      return session;
    } catch (error) {
      console.error('Error getting active session:', error);
      return null;
    }
  }
}

export const appMonitorManager = new AppMonitorManager();

