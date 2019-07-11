import React from "react";
import { Video } from "expo";
import {
  Alert,
  KeyboardAvoidingView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Button } from "react-native-elements";
import SplashMp4 from "./assets/splash.mp4";
import { ButtonContainerStyle, ButtonStyle } from "./Styles";
import { uploadFile } from "./Api";

export default class Splash extends React.Component {
  static navigationOptions = {
    header: null
  };
  constructor(props) {
    super(props);
  }

  render() {
    const {
      navigation: { navigate }
    } = this.props;

    return (
      <KeyboardAvoidingView style={styles.container}>
        <Video
          source={SplashMp4}
          shouldPlay={true}
          isLooping={true}
          resizeMode="cover"
          isMuted={true}
          style={styles.fullScreen}
        />
        <View style={[styles.fullScreen, styles.dimmer]} />

        <View style={ButtonContainerStyle.style}>
          <Button
            onPress={() => navigate("Login")}
            containerStyle={styles.button}
            buttonStyle={ButtonStyle.primary}
            title="Login"
          />
          <Button
            onPress={() => navigate("Register")}
            containerStyle={styles.button}
            buttonStyle={ButtonStyle.outline}
            titleStyle={ButtonStyle.orangeText}
            title="Register"
            type="outline"
          />
        </View>
      </KeyboardAvoidingView>
    );
  }
}

const styles = StyleSheet.create({
  button: {
    width: "60%",
    height: 60
  },
  container: {
    position: "absolute",
    top: 0,
    left: 0,
    bottom: 0,
    right: 0
  },
  fullScreen: {
    position: "absolute",
    top: 0,
    left: 0,
    bottom: 0,
    right: 0
  },
  dimmer: {
    backgroundColor: "#00000088"
  }
});
