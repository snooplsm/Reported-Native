import React from "react";

import { AppState, StyleSheet, Text, View } from "react-native";
import { Notifications } from "expo";
import { AppContainer } from "./Navigation";
import { api } from "./Api";

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
    console.log(notification);
    alert(JSON.stringify(notification));
  };

  render() {
    return <AppContainer />;
  }
}
