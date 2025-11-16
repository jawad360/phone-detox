import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

export default function RootLayout() {
  return (
    <>
      <StatusBar style="auto" />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen 
          name="permissions" 
          options={{ 
            headerShown: false,
            gestureEnabled: false,
          }} 
        />
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen
          name="add-app"
          options={{
            headerShown: true,
            title: 'Add App',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="app-settings"
          options={{
            headerShown: true,
            title: 'App Settings',
          }}
        />
      </Stack>
    </>
  );
}
