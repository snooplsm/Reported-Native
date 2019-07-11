import React from "react";
import {
  Alert,
  KeyboardAvoidingView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View
} from "react-native";
import {
  ErrorStyle,
  HorizontalStyle,
  ButtonContainerStyle,
  ButtonStyle
} from "./Styles";
import { Badge, Button, Input, Icon } from "react-native-elements";
import { api } from "./Api";

export default class Login extends React.Component {
  static navigationOptions = {
    title: "Login"
  };

  constructor(props) {
    super(props);
    this.email = React.createRef();
    this.state = {
      email: "",
      password: "",
      loading: false
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

  submitLogin() {
    if (this.state.loading) {
      return;
    }
    const {
      navigation: { navigate }
    } = this.props;
    this.setState({ loading: true, error: null });
    const { email, password } = this.state;
    api
      .login(email, password)
      .then(res => {
        this.setState({ loading: false });
        navigate("Home");
      })
      .catch(x => {
        this.setState({ loading: false });
        let message = "";
        if (x.response) {
          message = x.response.data.message;
        } else if (x.request) {
          message = "Server was unresponsive";
        } else {
          messaage = "Unknown error";
        }
        this.setState({ error: message });
        console.log("error logging in ", x);
      });
  }

  isSubmitEnabled() {
    return (
      this.validateEmail(this.state.email) && this.state.password.length > 2
    );
  }

  validateEmail(email) {
    var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
  }

  render() {
    return (
      <KeyboardAvoidingView style={styles.container}>
        <View style={{ marginTop: "10%" }} />
        <View
          style={{
            opacity: this.state.error ? 100 : 0,
            justifyContent: "center",
            alignItems: "center",
            flexDirection: "row"
          }}
        >
          <Badge status="error" />
          <Text> {this.state.error ?? "TAKE UP SPACE"}</Text>
        </View>
        <View style={{ marginTop: "10%" }} />
        <Input
          label={"Email"}
          autoCapitalize={"none"}
          autoFocus={true}
          keyboardType="email-address"
          ref={this.email}
          containerStyle={styles.email}
          errorStyle={ErrorStyle.style}
          errorMessage={this.state.emailError}
          onChangeText={email => this.onEmailChange(email)}
          onBlur={() => this.onEmailBlur()}
        />
        <Input
          label="Password"
          secureTextEntry={true}
          containerStyle={styles.field}
          errorStyle={ErrorStyle.style}
          onChangeText={password => this.setState({ password })}
        />
        <TouchableOpacity
          style={styles.forgotPassword}
          onPress={() => this.onForgotPassword()}
        >
          <Text>Forgot Password?</Text>
        </TouchableOpacity>
        <View style={styles.field}>
          <Button
            disabled={!this.isSubmitEnabled()}
            loading={this.state.loading}
            onPress={() => {
              this.submitLogin();
            }}
            buttonStyle={Object.assign(
              {
                padding: 20
              },
              Button.primary
            )}
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
    height: "100%"
  },
  email: {},
  field: {
    marginTop: 40
  },
  forgotPassword: {
    marginTop: 125,
    alignItems: "center"
  }
});
