import React from 'react';
import { Linking } from "react-native";
import * as Notifications from 'expo-notifications';
import { SafeAreaProvider } from "react-native-safe-area-context";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import 'expo-dev-client';

import { AppContainer } from "./Navigation";
import { appAnalytics } from "./analytics";

const handleNotification = (notification: Notifications.Notification) => {
  // console.log(notification);
  const data = notification.request.content.data;
  const url = data && data.url;
  if (typeof url === 'string') {
    Linking.openURL(url);
    return;
  }
};

export default function Index() {
  // console.log('firebase app', firebase?._options?.appId);

  React.useEffect(() => {
    const notificationSubscription = Notifications.addNotificationReceivedListener(handleNotification);
    appAnalytics.logAppOpen();
    return () => notificationSubscription.remove();
  }, []);

  console.log('app starting');

  return (
    <SafeAreaProvider>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <AppContainer />
      </GestureHandlerRootView>
    </SafeAreaProvider>
  );
}
