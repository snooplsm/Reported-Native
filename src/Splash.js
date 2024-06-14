import React from "react";
import { Video } from "expo-av";
import {
  Alert,
  KeyboardAvoidingView,
  StyleSheet,
  Linking,
  Text,
  View,
  Image
} from "react-native";
import { Button, Avatar } from "react-native-elements";
import SplashMp4 from "../assets/splash.mp4";
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
        {false &&
          <Video
            source={SplashMp4}
            shouldPlay={true}
            isLooping={true}
            resizeMode="cover"
            isMuted={true}
            style={styles.fullScreen}
          />
        }
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

        <Avatar
          rounded
          title={"@driversofnyc"}
          onPress={() => {
            Linking.openURL("https://twitter.com/driversofnyc");
          }}
          source={{
            uri:
              "https://pbs.twimg.com/profile_images/874464796426612738/J83zyXlh_400x400.jpg"
          }}
          containerStyle={{
            position: "absolute",
            opacity: 0.4,
            bottom: 40,
            right: 20,
            width: 50,
            height: 50
          }}
        />
        <Text
          style={{
            position: "absolute",
            bottom: 20,
            right: 20,
            color: "white"
          }}
        >
          @driversofnyc
        </Text>
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
    backgroundColor: "#00000011"
  }
});
