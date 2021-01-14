import React from "react";

import { Linking } from "react-native";
import { Notifications } from "expo";
import { AppContainer } from "./Navigation";
import { api } from "./Api";
import {SafeAreaProvider} from "react-native-safe-area-context";
import * as Sentry from 'sentry-expo';

Sentry.init({
  dsn: 'https://b430cf73db3e4ddcb167de15d97ac9c9@o503310.ingest.sentry.io/5588270',
  enableInExpoDevelopment: true,
  debug: true, // Sentry will try to print out useful debugging information if something goes wrong with sending an event. Set this to `false` in production.
});


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
