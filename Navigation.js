import Splash from "./Splash";
import Login from "./Login";
import Register from "./Register";
import Submission from "./Submission";
import Submissions from "./Submissions";
import CalendarView from "./CalendarView";
import AuthLoadingScreen from "./AuthLoadingScreen";
import FilterView from "./SubmissionFilter";
import Profile from "./Profile";
import { colors } from "./Styles";
import { Icon } from "react-native-elements";
import React from "react";
// import { createAppContainer, createSwitchNavigator } from "react-navigation";

import { createStackNavigator } from "@react-navigation/stack";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { Platform } from "react-native";
import { NavigationContainer } from "@react-navigation/native";

const defaultNavigationOptions = {
  headerStyle: {
    elevation: 0,
    shadowOpacity: 0
  }
};

const UserHomeStackNavigator = createStackNavigator();
const UserHomeStack = () => {
  const opts = {
    headerShown: true,
    title: () => { return (<></>) },
    headerStyle: { height: 43 },
  }

  return (
    <UserHomeStackNavigator.Navigator screenOptions={opts}>
      <UserHomeStackNavigator.Screen name="Submission" component={Submission} />
    </UserHomeStackNavigator.Navigator>
  )
}

const SubmissionsStackNavigator = createStackNavigator();
const SubmissionsStack = () => {
  const opts = {
    headerShown: true,
    title: () => { return (<></>) },
    headerStyle: { height: 43 },
  }

  return (
    <SubmissionsStackNavigator.Navigator screenOptions={opts}>
      <SubmissionsStackNavigator.Screen name="Submissions" component={Submissions} />
    </SubmissionsStackNavigator.Navigator>
  )
}

const ProfileStackNavigator = createStackNavigator();
const ProfileStack = () => {
  const opts = {
    headerShown: true,
    title: () => { return (<></>) },
    headerStyle: { height: 43 },
  }

  return (
    <ProfileStackNavigator.Navigator screenOptions={opts}>
      <ProfileStackNavigator.Screen name="Profile" component={Profile} />
    </ProfileStackNavigator.Navigator>
  )
}

/*
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
*/

const SignedInTabsNavigator = createBottomTabNavigator();
const SignedIn = () => {
  const opts = {
    headerShown: false,
  }

  const userHomeOpts = {
    tabBarLabel: 'Report',
    tabBarIcon: ({ color, size }) => (
      <Icon
        name="library-add"
        type="material"
        size={30}
        color={color}
      />
    ),
  }

  const submissionsOpts = {
    tabBarLabel: 'My Reports',
    tabBarIcon: ({ color, size }) => (
      <Icon
        name="list"
        type="material"
        size={30}
        color={color}
      />
    ),
  }

  const profileOpts = {
    tabBarLabel: 'Profile',
    tabBarIcon: ({ color, size }) => (
      <Icon
        name="face"
        type="material"
        size={size}
        color={color}
      />
    ),
  }

  return (
    <SignedInTabsNavigator.Navigator screenOptions={opts}>
      <SignedInTabsNavigator.Screen name="HomeStack" component={UserHomeStack} options={userHomeOpts} />
      <SignedInTabsNavigator.Screen name="SubmissionsStack" component={SubmissionsStack} options={submissionsOpts} />
      <SignedInTabsNavigator.Screen name="ProfileStack" component={ProfileStack} options={profileOpts} />
    </SignedInTabsNavigator.Navigator>
  )
}

const SignedOutNavigator = createStackNavigator();
const SignedOut = () => {
  const opts = {
    headerShown: false,
  }

  return (
    <SignedOutNavigator.Navigator screenOptions={opts}>
      <SignedOutNavigator.Screen name="Splash" component={Splash} />
      <SignedOutNavigator.Screen name="Login" component={Login} />
      <SignedOutNavigator.Screen name="Register" component={Register} />
    </SignedOutNavigator.Navigator>
  )
}

/*
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
    defaultNavigationOptions,
    tabBarOptions: {
      keyboardHidesTabBar: Platform.OS === "ios" ? false : true,
      activeTintColor: colors.orange,
      // activeBackgroundColor: "yellow", //Doesn't work
      showIcon: true,
      style: {
        height: 66
      },
      labelStyle: {
        fontSize: 14,
        paddingBottom: 4
      }
    }
  }
);
*/

/*
export const AppNavigator = createSwitchNavigator({
  AuthLoading: AuthLoadingScreen,
  Auth: SignedOutNavigator,
  Home: SignedIn
});
*/

const AppStackNavigator = createStackNavigator();

const AppNavigator = () => {
  const opts = {
    headerShown: false,
  };

  return (
    <AppStackNavigator.Navigator screenOptions={opts}>
      <AppStackNavigator.Screen name="SignedIn" component={SignedIn} />
      <AppStackNavigator.Screen name="SignedOut" component={SignedOut} />
    </AppStackNavigator.Navigator>
  )
}

// export const AppContainer = createAppContainer(AppNavigator);

export const AppContainer = () => {
  return (
    <NavigationContainer>
      <AppNavigator />
    </NavigationContainer>
  )
}
