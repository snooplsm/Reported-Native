import React from "react";

import { StyleSheet, Text, View, SafeAreaView } from "react-native";
import { Button } from "react-native-elements";
import { signOut, isSignedIn } from "./Auth";

export default class Profile extends React.Component {
  constructor(props) {
    super(props);
    this.state = {};
  }
  componentDidMount() {
    isSignedIn().then(user => {
      if (user) {
        this.setState({ user });
      }
    });
  }

  user() {
    if (!this.state.user) {
      return null;
    }
    const { user } = this.state;
    return (
      <>
        <Text>
          {user.firstName} {user.lastName}
        </Text>
        <Text>{user.email}</Text>
      </>
    );
  }

  render() {
    const {
      navigation: { navigate }
    } = this.props;

    return (
      <SafeAreaView>
        <Text>Profile</Text>
        {this.user()}
        <Button
          title="Signout"
          onPress={() => {
            signOut().then(f => {
              navigate("Auth");
            });
          }}
        />
      </SafeAreaView>
    );
  }
}
