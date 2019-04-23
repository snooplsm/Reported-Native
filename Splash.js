import React from 'react';
import { Video } from 'expo';
import { Alert, KeyboardAvoidingView, StyleSheet, Text, View} from 'react-native';
import { Button } from 'react-native-elements';
import SplashMp4 from './assets/splash.mp4';
import { ButtonContainerStyle, ButtonStyle } from './Styles';

export default class Splash extends React.Component {
  static navigationOptions = {
    header: null,
  };
  constructor(props) {
    super(props)
  }

  render() {
    return (
      <KeyboardAvoidingView style={styles.container}>
        <Video source={SplashMp4}
          shouldPlay={false}
          isLooping={false}
          resizeMode="cover"
          isMuted={true}
          style={styles.backgroundVideo} />

         <View style={ButtonContainerStyle.style}>
          <Button onPress={() => this.props.navigation.navigate('Login')} containerStyle={styles.button} buttonStyle={ButtonStyle.style} title="Login"/>
          <Button onPress={() => this.props.navigation.navigate('Register')} containerStyle={styles.button} buttonStyle={ButtonStyle.style} title="Register"/>
          <Button containerStyle={styles.button} buttonStyle={ButtonStyle.style} title="Skip"/>
         </View>
       </KeyboardAvoidingView>
    );
  }
}

const styles = StyleSheet.create({
  button: {
    width: '30%',
    height: 60
  },
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
  },
  backgroundVideo: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
}});
