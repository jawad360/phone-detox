import AsyncStorage from '@react-native-async-storage/async-storage';
import { AddictiveApp, UsageSession } from '../types';

const ADDICTIVE_APPS_KEY = '@addictive_apps';
const USAGE_SESSIONS_KEY = '@usage_sessions';
const COOLING_PERIODS_KEY = '@cooling_periods';

export async function getAddictiveApps(): Promise<AddictiveApp[]> {
  try {
    const data = await AsyncStorage.getItem(ADDICTIVE_APPS_KEY);
    return data ? JSON.parse(data) : [];
  } catch (error) {
    console.error('Error getting addictive apps:', error);
    return [];
  }
}

export async function saveAddictiveApp(app: AddictiveApp): Promise<void> {
  try {
    const apps = await getAddictiveApps();
    const existingIndex = apps.findIndex((a) => a.packageName === app.packageName);
    
    if (existingIndex >= 0) {
      apps[existingIndex] = app;
    } else {
      apps.push(app);
    }
    
    await AsyncStorage.setItem(ADDICTIVE_APPS_KEY, JSON.stringify(apps));
  } catch (error) {
    console.error('Error saving addictive app:', error);
    throw error;
  }
}

export async function removeAddictiveApp(packageName: string): Promise<void> {
  try {
    const apps = await getAddictiveApps();
    const filtered = apps.filter((a) => a.packageName !== packageName);
    await AsyncStorage.setItem(ADDICTIVE_APPS_KEY, JSON.stringify(filtered));
  } catch (error) {
    console.error('Error removing addictive app:', error);
    throw error;
  }
}

export async function getAddictiveApp(packageName: string): Promise<AddictiveApp | null> {
  try {
    const apps = await getAddictiveApps();
    return apps.find((a) => a.packageName === packageName) || null;
  } catch (error) {
    console.error('Error getting addictive app:', error);
    return null;
  }
}

export async function saveUsageSession(session: UsageSession): Promise<void> {
  try {
    const sessions = await getUsageSessions();
    sessions.push(session);
    await AsyncStorage.setItem(USAGE_SESSIONS_KEY, JSON.stringify(sessions));
  } catch (error) {
    console.error('Error saving usage session:', error);
  }
}

export async function getUsageSessions(): Promise<UsageSession[]> {
  try {
    const data = await AsyncStorage.getItem(USAGE_SESSIONS_KEY);
    return data ? JSON.parse(data) : [];
  } catch (error) {
    console.error('Error getting usage sessions:', error);
    return [];
  }
}

export async function setCoolingPeriod(packageName: string, endTime: number): Promise<void> {
  try {
    const data = await AsyncStorage.getItem(COOLING_PERIODS_KEY);
    const periods = data ? JSON.parse(data) : {};
    periods[packageName] = endTime;
    await AsyncStorage.setItem(COOLING_PERIODS_KEY, JSON.stringify(periods));
  } catch (error) {
    console.error('Error setting cooling period:', error);
  }
}

export async function getCoolingPeriod(packageName: string): Promise<number | null> {
  try {
    const data = await AsyncStorage.getItem(COOLING_PERIODS_KEY);
    const periods = data ? JSON.parse(data) : {};
    return periods[packageName] || null;
  } catch (error) {
    console.error('Error getting cooling period:', error);
    return null;
  }
}

export async function clearCoolingPeriod(packageName: string): Promise<void> {
  try {
    const data = await AsyncStorage.getItem(COOLING_PERIODS_KEY);
    const periods = data ? JSON.parse(data) : {};
    delete periods[packageName];
    await AsyncStorage.setItem(COOLING_PERIODS_KEY, JSON.stringify(periods));
  } catch (error) {
    console.error('Error clearing cooling period:', error);
  }
}
