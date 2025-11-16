const { withAndroidManifest } = require('@expo/config-plugins');

module.exports = function withCustomAndroidManifest(config) {
  return withAndroidManifest(config, async (config) => {
    const androidManifest = config.modResults;
    
    // Ensure manifest has xmlns:tools attribute
    if (!androidManifest.manifest.$['xmlns:tools']) {
      androidManifest.manifest.$['xmlns:tools'] = 'http://schemas.android.com/tools';
    }
    
    // Add permissions
    const permissions = [
      'android.permission.PACKAGE_USAGE_STATS',
      'android.permission.SYSTEM_ALERT_WINDOW',
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.WAKE_LOCK',
      'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
      'android.permission.QUERY_ALL_PACKAGES',
    ];
    
    if (!androidManifest.manifest['uses-permission']) {
      androidManifest.manifest['uses-permission'] = [];
    }
    
    permissions.forEach(permission => {
      const existing = androidManifest.manifest['uses-permission'].find(
        p => p.$['android:name'] === permission
      );
      
      if (!existing) {
        const permissionObj = {
          $: { 'android:name': permission }
        };
        
        // Add tools:ignore for QUERY_ALL_PACKAGES as it might trigger Play Store warnings
        if (permission === 'android.permission.QUERY_ALL_PACKAGES') {
          permissionObj.$['tools:ignore'] = 'QueryAllPackagesPermission';
        }
        
        androidManifest.manifest['uses-permission'].push(permissionObj);
      }
    });
    
    return config;
  });
};
