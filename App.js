import React from "react";

import { Linking } from "react-native";
import * as Notifications from 'expo-notifications';
import { AppContainer } from "./Navigation";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { GestureHandlerRootView } from "react-native-gesture-handler";

export default class App extends React.Component {
  constructor(props) {
    super(props);
  }

  componentDidMount() {
    this._notificationSubscription = Notifications.addNotificationReceivedListener(
      this._handleNotification
    );
  }

  _handleNotification = notification => {
    // console.log(notification);
    const data = notification.data;
    const url = data && data.url;
    if (url) {
      Linking.openURL(notification?.data?.url);
      return;
    }
  };

  render() {
    return (
      <SafeAreaProvider>
        <GestureHandlerRootView style={{ flex: 1 }}>
          <AppContainer />
        </GestureHandlerRootView>
      </SafeAreaProvider>
    )
  }
}
