import Splash from "./Splash";
import Login from "./Login";
import Register from "./Register";
import Submission from "./Submission";
import Submissions from "./Submissions";
import CalendarView from "./CalendarView";
import AuthLoadingScreen from "./AuthLoadingScreen";
import FilterView from "./SubmissionFilter";
import Profile from "./Profile";
import { Icon } from "react-native-elements";
import React from "react";
import {
  createStackNavigator,
  createAppContainer,
  createBottomTabNavigator,
  createSwitchNavigator
} from "react-navigation";

const SignedOutNavigator = createStackNavigator(
  {
    Splash: Splash,
    Login: Login,
    Register: Register,
    Submission: Submission
  },
  {
    initialRouteName: "Splash"
  }
);

const UserHomeStack = createStackNavigator({
  Submissions: {
    screen: Submissions
  }
});

const SignedInNavigator = createBottomTabNavigator({
  Home: {
    screen: UserHomeStack,
    navigationOptions: {
      tabBarLabel: "Home",
      tabBarIcon: ({ tintColor }) => (
        <Icon name="home" type="material" size={30} color={tintColor} />
      )
    }
  },
  Profile: {
    screen: Profile,
    navigationOptions: {
      tabBarLabel: "Profile",
      tabBarIcon: ({ tintColor }) => (
        <Icon name="face" type="material" size={30} color={tintColor} />
      )
    }
  }
});

export const AppNavigator = createSwitchNavigator({
  AuthLoading: AuthLoadingScreen,
  Auth: SignedOutNavigator,
  Home: SignedInNavigator
});

export const AppContainer = createAppContainer(AppNavigator);
