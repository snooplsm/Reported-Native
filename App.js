import React from 'react';
import Splash from './Splash';
import Login from './Login';
import Register from './Register';
import { StyleSheet, Text, View } from 'react-native';
import { createStackNavigator, createAppContainer } from "react-navigation";

const AppNavigator = createStackNavigator(
  {
    Home: Splash,
    Login: Login,
    Register: Register
  },
  {
    initialRouteName: 'Home'
  });

const AppContainer = createAppContainer(AppNavigator)

AppContainer.header

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});

export default class App extends React.Component {
  render() {
    return (
      <AppContainer/>
    );
  }
}
