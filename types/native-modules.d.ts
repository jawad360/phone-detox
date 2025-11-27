import 'react-native';

interface InstalledAppsInterface {
  getApps(): Promise<Array<{
    packageName: string;
    appName: string;
  }>>;
}

interface AppMonitorInterface {
  startMonitoring(): void;
  stopMonitoring(): void;
  updateMonitoredApps(apps: Array<{ packageName: string }>): void;
  updateAppConfig(packageName: string, config: {
    behavior?: string;
    coolingPeriodMinutes?: number;
  }): void;
  setCoolingPeriod(packageName: string, endTime: number): void;
  getActiveSession(packageName: string): Promise<{
    packageName: string;
    startTime: number;
    requestedMinutes: number;
    behavior: string;
  } | null>;
}

export interface NativeModules {
  InstalledApps: InstalledAppsInterface;
  AppMonitor: AppMonitorInterface;
}

declare module 'react-native' {
  interface NativeModulesStatic {
    InstalledApps: InstalledAppsInterface;
    AppMonitor: AppMonitorInterface;
  }
}

