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

export default class Login extends React.Component {
  static navigationOptions = {
    title: "Login",
  };

  constructor(props) {
    super(props);
    this.email = React.createRef();
    this.state = {
      email: "",
      password: "",
      loading: false,
    };
  }

  onEmailChange(email) {
    const state = Object.assign({}, this.state);
    state.email = email;
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

  onForgotPassword() {
    const { email } = this.state;
    if (!this.validateEmail(email)) {
      let message = "";
      if (email == "") {
        message = "Type in your email address to change the password.";
      } else {
        message = `Your email address '${email}' is invalid.  Update it to change the password.`;
      }
      Alert.alert("Invalid Email", message);
    }
  }

  validateEmail(email) {
    var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
  }

  render() {
    return (
      <KeyboardAvoidingView style={styles.container}>
        <Input
          label={"Email"}
          autoCapitalize={"none"}
          keyboardType="email-address"
          ref={this.email}
          containerStyle={styles.email}
          errorStyle={ErrorStyle.style}
          errorMessage={this.state.emailError}
          onChangeText={email => this.onEmailChange(email)}
          onBlur={() => this.onEmailBlur()}
          leftIcon={<Icon type="material-community" name="email" />}
        />
        <Input
          label="Password"
          secureTextEntry={true}
          containerStyle={styles.field}
          errorStyle={ErrorStyle.style}
          leftIcon={<Icon type="material-community" name="lock" />}
        />
        <TouchableOpacity
          style={styles.forgotPassword}
          onPress={() => this.onForgotPassword()}
        >
          <Text>Forgot Password?</Text>
        </TouchableOpacity>
        <View style={styles.field}>
          <Button
            loading={this.state.loading}
            onPress={() => {}}
            buttonStyle={ButtonStyle.primary}
            title="Login"
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
  forgotPassword: {
    marginTop: 125,
    alignItems: "center",
  },
});
