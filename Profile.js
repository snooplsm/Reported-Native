import React from "react";

import {
  Alert,
  StyleSheet,
  Text,
  TouchableOpacity, View,
} from "react-native";
import { SafeAreaView } from 'react-native-safe-area-context';
import { Input, Icon } from "react-native-elements";
import LogoTitle from "./LogoTitle";
import { ErrorStyle, globalStyles } from "./Styles";
import { api } from "./Api";
import { signOut, isSignedIn } from "./Auth";
import FloatingMainButton from "./FloatingMainButton";
import { KeyboardAwareScrollView } from "react-native-keyboard-aware-scroll-view";

export default class Profile extends React.Component {
  static navigationOptions = ({ navigation }) => {
    return {
      headerTitle: <LogoTitle title={"PROFILE"} />,
      headerRight: (
        <TouchableOpacity
          onPress={navigation.getParam("logoutPressed")}
          style={{ padding: 10 }}
          color="#000"
        >
          <Text>LOGOUT</Text>
        </TouchableOpacity>
      ),
      headerLeft: (
        <TouchableOpacity
          onPress={navigation.getParam("editPressed")}
          style={{ padding: 10 }}
          name="arrow-back"
          color="#000"
        >
          <Text>{navigation.getParam("editText")}</Text>
        </TouchableOpacity>
      )
    };
  };

  logoutPressed = () => {
    signOut().then(() => {
      this.props.navigation.navigate("Auth");
    });
  };

  editPressed = () => {
    const { editable } = this.state;
    this.setState({ editable: !editable });
    if (editable) {
      this.setData(this.state.user);
    }
    const editMessage = editable ? "EDIT" : "CANCEL";
    this.props.navigation.setParams({
      editText: editMessage
    });
  };

  constructor(props) {
    super(props);
    this.state = {
      editable: false
    };
  }

  setData(user) {
    this.setState({
      user,
      firstName: user.firstName,
      lastName: user.lastName,
      phone: user.phone,
      email: user.email
    });
  }

  componentDidMount() {
    isSignedIn().then(user => {
      if (user) {
        this.setData(user);
      }
    });
    this.props.navigation.setParams({
      logoutPressed: this.logoutPressed,
      editPressed: this.editPressed,
      editText: "EDIT"
    });
  }

  onChange = field => value => {
    this.setState({ [field]: value });
  }

  onFirstNameBlur() {
    let firstNameError = null;
    if (this.state.firstName.length == 0) {
      firstNameError = "First Name required";
    } else {
      firstNameError = null;
    }
    this.setState({ firstNameError });
  }

  onLastNameBlur() {
    let lastNameError = null;
    if (this.state.lastName.length == 0) {
      lastNameError = "First Name required";
    } else {
      lastNameError = null;
    }
    this.setState({ lastNameError });
  }

  onPhoneBlur() {
    const phone = this.state.phone.replace(/\D/g, "");
    let phoneError = "";
    if (phone.length == 0) {
      phoneError = "Phone required";
    } else if (phone.length !== 10) {
      phoneError = "Phone invalid";
    } else {
      phoneError = "";
    }
    this.setState({ phoneError });
  }

  onLastNameBlur() {
    let lastNameError = null;
    if (this.state.lastName.length == 0) {
      lastNameError = "Last Name required";
    } else {
      lastNameError = null;
    }
    this.setState({ lastNameError });
  }

  onEmailBlur() {
    let emailError = null;
    if (this.state.email.length != 0 && !this.validateEmail(this.state.email)) {
      emailError = "Invalid Email";
    } else {
      emailError = null;
    }
    this.setState({ emailError });
  }

  validateEmail(email) {
    var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
  }

  updateUser() {
    const { email, phone, firstName, lastName } = this.state;
    const newUser = { email, phone, firstName, lastName };
    api
      .updateUser(newUser)
      .then(success => success.data)
      .then(success => {
        this.setData(success);
        this.editPressed();
      })
      .catch(() => {
        Alert.alert("Problem", "Could not update.");
      });
  }

  render() {
    const { editable, user } = this.state;
    return (
      <View style={styles.mainWrapper}>
        {user &&
          <KeyboardAwareScrollView style={globalStyles.mainContainer}>
            <Input
              label={"First Name"}
              ref={this.firstName}
              editable={this.state.editable}
              containerStyle={styles.field}
              value={this.state.firstName}
              errorStyle={ErrorStyle.style}
              errorMessage={this.state.firstNameError}
              onChangeText={this.onChange('firstName')}
              onBlur={() => this.onFirstNameBlur()}
              leftIcon={<Icon type="material" name="person" />}
            />
            <Input
              label={"Last Name"}
              ref={this.firstName}
              value={this.state.lastName}
              editable={this.state.editable}
              containerStyle={styles.field}
              errorStyle={ErrorStyle.style}
              errorMessage={this.state.lastNameError}
              onChangeText={this.onChange('lastName')}
              onBlur={() => this.onLastNameBlur()}
              leftIcon={<Icon type="material" name="person" />}
            />
            <Input
              label={"Phone"}
              keyboardType={"phone-pad"}
              editable={this.state.editable}
              value={this.state.phone}
              ref={this.phone}
              containerStyle={styles.field}
              errorStyle={ErrorStyle.style}
              errorMessage={this.state.phoneError}
              onChangeText={this.onChange('phone')}
              onBlur={() => this.onPhoneBlur()}
              leftIcon={<Icon type="material-community" name="phone" />}
            />
            <Input
              label={"Email"}
              autoCapitalize={"none"}
              keyboardType="email-address"
              editable={this.state.editable}
              ref={this.email}
              value={this.state.email}
              containerStyle={styles.field}
              errorStyle={ErrorStyle.style}
              errorMessage={this.state.emailError}
              onChangeText={this.onChange('email')}
              onBlur={() => this.onEmailBlur()}
              leftIcon={<Icon type="material-community" name="email" />}
            />
          </KeyboardAwareScrollView>
        }
        {
          editable &&
          <FloatingMainButton
            isEnabled
            onPress={() => this.updateUser()}
            title={'Save'}
            containerStyle={styles.saveButton}
          />
        }
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    flex: 1,
    width: "100%",
    height: "100%"
  },
  email: {
    top: "20%"
  },
  field: {
    marginTop: 30
  },
  alreadyRegistered: {
    marginTop: 30,
    alignItems: "center"
  },
  saveButton: {
    paddingHorizontal: 10,
    paddingVertical: 5
  },
  mainWrapper: {
    flex: 1,
  },
});
