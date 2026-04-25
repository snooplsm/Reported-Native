import AuthProvider, { useAuth } from './AuthProvider';
import Loading from './Loading';
import Splash from "./Splash";
import Login from "./Login";
import Register from "./Register";
import Submission from "./Submission";
import Submissions from "./Submissions";
// import CalendarView from "./CalendarView";
// import AuthLoadingScreen from "./AuthLoadingScreen";
// import FilterView from "./SubmissionFilter";
import Profile from "./Profile";
// import { colors } from "./Styles";
import { Icon } from "react-native-elements";
import React from "react";
// import { createAppContainer, createSwitchNavigator } from "react-navigation";

import { createStackNavigator } from "@react-navigation/stack";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { NavigationContainer } from "@react-navigation/native";
import { appAnalytics } from "./analytics";

const defaultNavigationOptions = {
  headerStyle: {
    elevation: 0,
    shadowOpacity: 0
  }
};

const UserHomeStackNavigator = createStackNavigator();
const UserHomeNavigator = UserHomeStackNavigator.Navigator as React.ComponentType<any>;
const UserHomeStack = () => {
  const opts = {
    headerShown: true,
    title: 'Submission',
    // title: () => { return (<></>) },
    // headerStyle: { height: 43 },
  }

  return (
    <UserHomeNavigator screenOptions={opts}>
      <UserHomeStackNavigator.Screen name="Submission" component={Submission} />
    </UserHomeNavigator>
  )
}

const SubmissionsStackNavigator = createStackNavigator();
const SubmissionsNavigator = SubmissionsStackNavigator.Navigator as React.ComponentType<any>;
const SubmissionsStack = () => {
  const opts = {
    headerShown: true,
    title: 'Reports',
    // title: () => { return (<></>) },
    // headerStyle: { height: 43 },
  }

  return (
    <SubmissionsNavigator screenOptions={opts}>
      <SubmissionsStackNavigator.Screen name="Submissions" component={Submissions} />
    </SubmissionsNavigator>
  )
}

const ProfileStackNavigator = createStackNavigator();
const ProfileNavigator = ProfileStackNavigator.Navigator as React.ComponentType<any>;
const ProfileStack = () => {
  const opts = {
    headerShown: true,
    title: 'Profile',
    // title: () => { return (<></>) },
    // headerStyle: { height: 43 },
  }

  return (
    <ProfileNavigator screenOptions={opts}>
      <ProfileStackNavigator.Screen name="Profile" component={Profile} />
    </ProfileNavigator>
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
const SignedInTabs = SignedInTabsNavigator.Navigator as React.ComponentType<any>;
const SignedIn = () => {
  const opts = {
    headerShown: false,
  }

  const userHomeOpts = {
    tabBarLabel: 'Report',
    tabBarIcon: ({ color }: { color: string; size: number }) => (
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
    tabBarIcon: ({ color }: { color: string; size: number }) => (
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
    tabBarIcon: ({ color, size }: { color: string; size: number }) => (
      <Icon
        name="face"
        type="material"
        size={size}
        color={color}
      />
    ),
  }

  return (
    <SignedInTabs screenOptions={opts}>
      <SignedInTabsNavigator.Screen name="HomeStack" component={UserHomeStack} options={userHomeOpts} />
      <SignedInTabsNavigator.Screen name="SubmissionsStack" component={SubmissionsStack} options={submissionsOpts} />
      <SignedInTabsNavigator.Screen name="ProfileStack" component={ProfileStack} options={profileOpts} />
    </SignedInTabs>
  )
}

const SignedOutNavigator = createStackNavigator();
const SignedOutStack = SignedOutNavigator.Navigator as React.ComponentType<any>;
const SignedOut = () => {
  const opts = {
    headerShown: false,
  }

  return (
    <SignedOutStack screenOptions={opts}>
      <SignedOutNavigator.Screen name="Splash" component={Splash} />
      <SignedOutNavigator.Screen name="Login" component={Login} />
      <SignedOutNavigator.Screen name="Register" component={Register} />
    </SignedOutStack>
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
const AppStack = AppStackNavigator.Navigator as React.ComponentType<any>;

const AppNavigator = () => {
  const auth = useAuth();
  // console.log('auth', auth);

  const opts = {
    headerShown: false,
  };

  return (
    <AppStack screenOptions={opts}>
      {auth.loading ?
        <AppStackNavigator.Screen name="Loading" component={Loading} />
        :
        auth.authorized ?
          <AppStackNavigator.Screen name="SignedIn" component={SignedIn} />
          :
          <AppStackNavigator.Screen name="SignedOut" component={SignedOut} />
      }
    </AppStack>
  )
}

// export const AppContainer = createAppContainer(AppNavigator);

export const AppContainer = () => {
  const routeNameRef = React.useRef<string | undefined>(undefined);
  const navigationRef = React.useRef<any>(null);

  return (
    <AuthProvider>
      <NavigationContainer
        ref={navigationRef}
        onReady={() => {
          const currentRouteName = navigationRef.current?.getCurrentRoute()?.name;
          routeNameRef.current = currentRouteName;
          if (currentRouteName) {
            appAnalytics.logScreenView(currentRouteName);
          }
        }}
        onStateChange={() => {
          const previousRouteName = routeNameRef.current;
          const currentRouteName = navigationRef.current?.getCurrentRoute()?.name;

          if (currentRouteName && previousRouteName !== currentRouteName) {
            routeNameRef.current = currentRouteName;
            appAnalytics.logScreenView(currentRouteName);
          }
        }}
      >
        <AppNavigator />
      </NavigationContainer>
    </AuthProvider>
  )
}
