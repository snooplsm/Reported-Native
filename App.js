import React from "react";

import { AppState, StyleSheet, Text, View } from "react-native";
import { AppContainer } from "./Navigation";
import { api } from "./Api";

export default class App extends React.Component {
  constructor(props) {
    super(props);
    this.registering = false;
  }

  register = async () => {
    if (this.registering) {
      return;
    }
    this.registering = true;
    api
      .registerToken()
      .then(success => {
        console.log("token registered");
        this.registering = false;
      })
      .catch(e => {
        this.regisgtering = false;
        console.log("token error", e);
      });
  };

  componentDidMount() {
    AppState.addEventListener("change", this._handleAppStateChange);
    this.register();
  }

  componentWillUnmount() {
    console.log("unmount");
    AppState.removeEventListener("change", this._handleAppStateChange);
  }

  _handleAppStateChange(state) {
    console.log(state);
    if (state === "active") {
      this.register();
    }
  }

  render() {
    return <AppContainer />;
  }
}
