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

const defaultNavigationOptions = {
  headerStyle: {
    elevation: 0,
    shadowOpacity: 0
  }
};

const UserHomeStack = createStackNavigator(
  {
    Submission: {
      screen: Submission
    }
  },

  {
    defaultNavigationOptions
  }
);

const SubmissionsStack = createStackNavigator(
  {
    Submissions: {
      screen: Submissions
    }
  },
  {
    defaultNavigationOptions
  }
);

const SignedOutNavigator = createStackNavigator(
  {
    Splash: Splash,
    Login: Login,
    Register: Register,
    Submission: Submissions
  },
  {
    initialRouteName: "Splash",
    defaultNavigationOptions
  }
);

const ProfileStack = createStackNavigator(
  {
    Profile: {
      screen: Profile
    }
  },
  {
    defaultNavigationOptions
  }
);

const SignedInNavigator = createBottomTabNavigator(
  {
    Home: {
      screen: UserHomeStack,
      navigationOptions: {
        tabBarLabel: "Report",
        tabBarIcon: ({ tintColor }) => (
          <Icon
            name="library-add"
            type="material"
            size={30}
            color={tintColor}
          />
        )
      }
    },
    Submissions: {
      screen: SubmissionsStack,
      navigationOptions: {
        tabBarLabel: "My Reports",
        tabBarIcon: ({ tintColor }) => (
          <Icon name="list" type="material" size={30} color={tintColor} />
        )
      }
    },
    Profile: {
      screen: ProfileStack,
      navigationOptions: {
        tabBarLabel: "Profile",
        tabBarIcon: ({ tintColor }) => (
          <Icon name="face" type="material" size={30} color={tintColor} />
        )
      }
    }
  },
  {
    defaultNavigationOptions
  }
);

export const AppNavigator = createSwitchNavigator({
  AuthLoading: AuthLoadingScreen,
  Auth: SignedOutNavigator,
  Home: SignedInNavigator
});

export const AppContainer = createAppContainer(AppNavigator);
