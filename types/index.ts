export interface AddictiveApp {
  packageName: string;
  appName: string;
  behavior: 'ask' | 'stop';
  coolingPeriodMinutes: number;
  isInCooling?: boolean;
  coolingEndTime?: number;
}

export interface UsageSession {
  packageName: string;
  startTime: number;
  requestedMinutes: number;
  endTime?: number;
}

export interface InstalledApp {
  packageName: string;
  appName: string;
}
