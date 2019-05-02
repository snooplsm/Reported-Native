import React from "react";
import {
  Alert,
  KeyboardAvoidingView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { ErrorStyle, ButtonContainerStyle, ButtonStyle } from "./Styles";
import { Button, Input, Icon } from "react-native-elements";

export default class Register extends React.Component {
  static navigationOptions = {
    title: "Register",
  };

  constructor(props) {
    super(props);
    this.email = React.createRef();
    this.state = {
      email: "",
      firstName: "",
      lastName: "",
      password: "",
      phone: "",
      loading: false,
    };
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

  onAlreadyRegistered() {
    this.props.navigation.replace("Login");
  }

  onFirstNameBlur() {
    const state = Object.assign({}, this.state);
    if (this.state.firstName.length != 0) {
      state.firstNameError = "First Name required";
    } else {
      state.firstNameError = null;
    }
    this.setState(state);
  }

  onLastNameBlur() {
    const state = Object.assign({}, this.state);
    if (this.state.lastName.length != 0) {
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
    if (this.state.lastName.length != 0) {
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

  render() {
    return (
      <KeyboardAvoidingView style={styles.container}>
        <Input
          label={"First Name"}
          ref={this.firstName}
          containerStyle={styles.field}
          errorStyle={ErrorStyle.style}
          errorMessage={this.state.firstNameError}
          onChangeText={firstName => this.onFirstNameChange(firstName)}
          onBlur={() => this.onFirstNameBlur()}
          leftIcon={<Icon type="material" name="person" />}
        />
        <Input
          label={"Last Name"}
          ref={this.firstName}
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
          ref={this.firstName}
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
          ref={this.field}
          containerStyle={styles.field}
          errorStyle={ErrorStyle.style}
          errorMessage={this.state.emailError}
          onChangeText={email => this.onEmailChange(email)}
          onBlur={() => this.onEmailBlur()}
          leftIcon={<Icon type="material-community" name="email" />}
        />
        <Input
          label="Password (optional)"
          secureTextEntry={true}
          containerStyle={styles.field}
          errorStyle={ErrorStyle.style}
          leftIcon={<Icon type="material-community" name="lock" />}
        />
        <TouchableOpacity
          style={styles.alreadyRegistered}
          onPress={() => this.onAlreadyRegistered()}
        >
          <Text>Already Registered? Login</Text>
        </TouchableOpacity>
        <View style={styles.field}>
          <Button
            onPress={() => {}}
            loading={this.state.loading}
            buttonStyle={ButtonStyle.primary}
            title="Register"
          />
        </View>
      </KeyboardAvoidingView>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    position: "absolute",
    flex: 1,
    width: "100%",
    height: "100%",
  },
  email: {
    top: "20%",
  },
  field: {
    marginTop: 30,
  },
  alreadyRegistered: {
    marginTop: 30,
    alignItems: "center",
  },
});
