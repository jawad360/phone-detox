import { useEffect, useState } from 'react';
import { Redirect } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { View, ActivityIndicator } from 'react-native';

const PERMISSIONS_GRANTED_KEY = '@permissions_granted';

export default function Index() {
  const [permissionsChecked, setPermissionsChecked] = useState(false);
  const [hasPermissions, setHasPermissions] = useState(false);

  useEffect(() => {
    checkPermissions();
  }, []);

  const checkPermissions = async () => {
    try {
      const permissionsStatus = await AsyncStorage.getItem(PERMISSIONS_GRANTED_KEY);
      setHasPermissions(!!permissionsStatus);
    } catch (error) {
      console.error('Error checking permissions:', error);
      setHasPermissions(false);
    } finally {
      setPermissionsChecked(true);
    }
  };

  if (!permissionsChecked) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F2F2F7' }}>
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  return <Redirect href={hasPermissions ? '/(tabs)' : '/permissions'} />;
}
