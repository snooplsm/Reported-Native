import React from "react";

import { isSignedIn } from "./Auth";

type AuthLoadingScreenProps = {
  navigation: {
    navigate: (routeName: string) => void;
  };
};

export default class AuthLoadingScreen extends React.Component<AuthLoadingScreenProps> {
  constructor(props: AuthLoadingScreenProps) {
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
