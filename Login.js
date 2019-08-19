import React from "react";
import {
  Alert,
  KeyboardAvoidingView,
  ScrollView,
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
import LogoTitle from "./LogoTitle";
import { Badge, Button, Input, Icon } from "react-native-elements";
import { api } from "./Api";

export default class Login extends React.Component {
  static navigationOptions = {
    headerTitle: <LogoTitle title="LOGIN" />,
    headerRight: (
      <Icon
        containerStyle={{ padding: 10, opacity: 0 }}
        name="arrow-back"
        color="#000"
      />
    )
  };

  constructor(props) {
    super(props);
    this.email = React.createRef();
    this.state = {
      error: undefined,
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
    let emailError = null;
    if (this.state.email.length != 0 && !this.validateEmail(this.state.email)) {
      emailError = "Invalid Email";
    } else {
      emailError = null;
    }
    this.setState({ emailError });
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
    } else {
      api
        .forgotPassword({ email })
        .then(fun => {
          Alert.alert("", "Password reset email has been sent.");
        })
        .catch(e => {
          Alert.alert("", "There was an error.");
        });
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
          if (x.response.status == 401) {
            message = "Credentials not found";
          } else {
            message = x.response.data.message;
          }
        } else if (x.request) {
          message = "Server was unresponsive";
        } else {
          messaage = "Unknown error";
        }
        this.setState({ error: message });
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
      <KeyboardAvoidingView behavior="padding" style={styles.container}>
        <ScrollView style={styles.scrollView}>
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
        </ScrollView>
        <Button
          disabled={!this.isSubmitEnabled()}
          loading={this.state.loading}
          onPress={() => {
            this.submitLogin();
          }}
          containerStyle={{
            marginBottom: 88
          }}
          buttonStyle={Object.assign(
            {
              padding: 20,
              borderRadius: 0
            },
            Button.primary
          )}
          title="Login"
        />
      </KeyboardAvoidingView>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1
  },
  scrollView: {
    paddingHorizontal: 20
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
