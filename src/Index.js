import React from 'react';
// import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Linking } from "react-native";
import * as Notifications from 'expo-notifications';
import { SafeAreaProvider } from "react-native-safe-area-context";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import 'expo-dev-client';

// import analytics from './firebaseConfig';
// import analytics from "@react-native-firebase/analytics";

import { AppContainer } from "./Navigation";

const handleNotification = (notification) => {
  // console.log(notification);
  const data = notification.data;
  const url = data && data.url;
  if (url) {
    Linking.openURL(notification?.data?.url);
    return;
  }
};

export default function Index() {
  // console.log('firebase app', firebase?._options?.appId);

  React.useEffect(() => {
    const notificationSubscription = Notifications.addNotificationReceivedListener(handleNotification);
  }, []);

  console.log('app starting');
  // console.log('ana', analytics);
  // analytics().logEvent('started', {});

  return (
    <SafeAreaProvider>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <AppContainer />
      </GestureHandlerRootView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
