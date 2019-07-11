import React from "react";

import {
  StyleSheet,
  Text,
  View,
  SafeAreaView,
  TouchableOpacity,
  KeyboardAvoidingView
} from "react-native";
import { Button, Input, Icon } from "react-native-elements";
import { ErrorStyle, ButtonContainerStyle, ButtonStyle } from "./Styles";
import { signOut, isSignedIn } from "./Auth";

export default class Profile extends React.Component {
  static navigationOptions = ({ navigation }) => {
    return {
      headerTitle: "Profile",
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
    signOut().then(r => {
      this.props.navigation.navigate("Auth");
    });
  };

  editPressed = () => {
    const { editable } = this.state;
    console.log(editable, !editable);
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

  onEmailChange(email) {
    const state = Object.assign({}, this.state);
    state.email = email;
    this.setState(state);
  }

  onFirstNameChange(firstName) {
    const state = Object.assign({}, this.state);
    state.firstName = firstName;
    this.setState(state);
  }

  onLastNameChange(lastName) {
    const state = Object.assign({}, this.state);
    state.lastName = lastName;
    this.setState(state);
  }

  onFirstNameBlur() {
    const state = Object.assign({}, this.state);
    if (this.state.firstName.length == 0) {
      state.firstNameError = "First Name required";
    } else {
      state.firstNameError = null;
    }
    this.setState(state);
  }

  onLastNameBlur() {
    const state = Object.assign({}, this.state);
    if (this.state.lastName.length == 0) {
      state.lastNameError = "First Name required";
    } else {
      state.lastNameError = null;
    }
    this.setState(state);
  }

  onPhoneChange(phone) {
    const state = Object.assign({}, this.state);
    state.phone = phone;
    this.setState(state);
  }

  onPhoneBlur() {
    const state = Object.assign({}, this.state);
    const phone = this.state.phone.replace(/\D/g, "");
    if (phone.length == 0) {
      state.phoneError = "Phone required";
    } else if (phone.length !== 10) {
      state.phoneError = "Phone invalid";
    } else {
      state.phoneError = "";
    }
    this.setState(state);
  }

  onLastNameBlur() {
    const state = Object.assign({}, this.state);
    if (this.state.lastName.length == 0) {
      state.lastNameError = "Last Name required";
    } else {
      state.lastNameError = null;
    }
    this.setState(state);
  }

  onEmailBlur() {
    const state = Object.assign({}, this.state);
    if (this.state.email.length != 0 && !this.validateEmail(this.state.email)) {
      state.emailError = "Invalid Email";
    } else {
      state.emailError = null;
    }
    this.setState(state);
  }

  validateEmail(email) {
    var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
  }

  user() {
    if (!this.state.user) {
      return null;
    }
    const { user } = this.state;
    return (
      <>
        <KeyboardAvoidingView style={styles.container}>
          <Input
            label={"First Name"}
            ref={this.firstName}
            editable={this.state.editable}
            containerStyle={styles.field}
            value={this.state.firstName}
            errorStyle={ErrorStyle.style}
            errorMessage={this.state.firstNameError}
            onChangeText={firstName => this.onFirstNameChange(firstName)}
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
            onChangeText={lastName => this.onLastNameChange(lastName)}
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
            onChangeText={email => this.onPhoneChange(email)}
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
            onChangeText={email => this.onEmailChange(email)}
            onBlur={() => this.onEmailBlur()}
            leftIcon={<Icon type="material-community" name="email" />}
          />
        </KeyboardAvoidingView>
      </>
    );
  }

  render() {
    const {
      navigation: { navigate }
    } = this.props;

    return (
      <>
        {this.user()}
        <Button
          containerStyle={{
            bottom: 0,
            width: "100%",
            position: "absolute"
          }}
          buttonStyle={{
            padding: 20
          }}
          title="Update"
          onPress={() => {
            signOut().then(f => {
              navigate("Auth");
            });
          }}
        />
      </>
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
  }
});
