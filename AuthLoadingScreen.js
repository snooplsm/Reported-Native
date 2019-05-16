import React from "react";

import { StyleSheet, Text, View } from "react-native";
import { AppContainer } from "./Navigation";
import { isSignedIn } from "./Auth";

export default class AuthLoadingScreen extends React.Component {
  constructor(props) {
    super(props);
    this._bootstrapAsync();
  }

  _bootstrapAsync = async () => {
    const userToken = await isSignedIn();

    // This will switch to the App screen or Auth screen and this loading
    // screen will be unmounted and thrown away.
    this.props.navigation.navigate(userToken ? "Home" : "Auth");
  };

  render() {
    return null;
  }
}
