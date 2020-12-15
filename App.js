import React from "react";

import { AppState, StyleSheet, Text, View, Linking } from "react-native";
import { Notifications } from "expo";
import { AppContainer } from "./Navigation";
import { api } from "./Api";
import {SafeAreaProvider} from "react-native-safe-area-context/src/index";

export default class App extends React.Component {
  constructor(props) {
    super(props);
  }

  componentDidMount() {
    this._notificationSubscription = Notifications.addListener(
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
          <AppContainer />
        </SafeAreaProvider>
    )
  }
}
