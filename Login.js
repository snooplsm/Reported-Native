import React from "react";
import {
  Alert,
  KeyboardAvoidingView,
  Keyboard,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View
} from "react-native";
import { ErrorStyle } from "./Styles";
import LogoTitle from "./LogoTitle";
import { Badge, Button, Input, Icon } from "react-native-elements";
import { api } from "./Api";
import { validateEmail, seconds } from "./helpers/AccountHelpers";

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
      loading: false,
      pw_reset_submitted_at: null,
    };
  }

  onEmailChange(email) {
    const state = Object.assign({}, this.state);
    state.email = email;
    this.setState(state);
  }

  onEmailBlur() {
    const { email } = this.state;
    let emailError = null;
    if (email.length != 0 && !validateEmail(email)) {
      emailError = "Invalid Email";
    } else {
      emailError = null;
    }
    this.setState({ emailError });
  }

  onForgotPassword() {
    const { email, pw_reset_submitted_at } = this.state;

    if (pw_reset_submitted_at > Date.now() - seconds(10)) {
      Alert.alert("Duplicate request", "Please wait a few seconds before attempting to reset your password.");
      return;
    }

    if (!validateEmail(email)) {
      let message = "";
      if (email == "") {
        message = "Type in your email address to change the password.";
      } else {
        message = `Your email address '${email}' is invalid.  Update it to change the password.`;
      }
      Alert.alert("Invalid Email", message);
    } else {
      this.setState({ pw_reset_submitted_at: Date.now() });
      Alert.alert("", "A password reset email has been sent. It may take a couple of minutes to receive it.");

      api
        .forgotPassword({ email })
        .then(_ => null)
        .catch(_e => {
          // NOTE: This error might appear up to 30s after the reset button is clicked due
          // to a network timeout. 
          Alert.alert("", "There was an error resetting your password. Please try again later.");
        });
    }
  }

  submitLogin() {
    const { email, loading, password } = this.state;
    if (loading) { return }
    const {
      navigation: { navigate }
    } = this.props;
    this.setState({ loading: true, error: null });

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
        this.setState({ loading: false, error: message });
      });
  }

  _keyboardDidShow() {
    this.setState({ keyboard: true });
  }

  _keyboardDidHide() {
    this.setState({ keyboard: false });
  }

  componentDidMount() {
    this.keyboardDidShowListener = Keyboard.addListener(
      "keyboardDidShow",
      this._keyboardDidShow.bind(this)
    );
    this.keyboardDidHideListener = Keyboard.addListener(
      "keyboardDidHide",
      this._keyboardDidHide.bind(this)
    );
  }

  componentWillUnmount() {
    this.keyboardDidShowListener.remove();
    this.keyboardDidHideListener.remove();
  }

  isSubmitEnabled() {
    const { email, password } = this.state;
    return validateEmail(email) && password.length > 2;
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
            label="Email"
            autoCapitalize="none"
            autoFocus
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
            secureTextEntry
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
            marginBottom: this.state.keyboard ? 64 : 0
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
    padding: 10,
    alignItems: "center"
  }
});
