import React from "react";
import {
  Alert,
  Keyboard,
  StyleSheet,
  Text,
  View,
  ScrollView,
} from "react-native";
import {
  ErrorStyle,
  colors,
  ButtonStyle,
  globalStyles,
  ButtonContainerStyle,
} from "./Styles";
import { SafeAreaView } from 'react-native-safe-area-context';
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
    return (
      this.validateEmail(this.state.email) && this.state.password.length > 2
    );
  }

  validateEmail(email) {
    var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
  }

  render() {
    const { keyboard } = this.state;
    return (
      <SafeAreaView style={globalStyles.mainContainer}>
        <ScrollView>
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
          <Button
            loading={this.state.loading}
            background={null}
            onPress={() => this.onForgotPassword()}
            buttonStyle={{
              ...ButtonStyle.outline,
              height: 60,
            }}
            containerStyle={{
              borderColor: colors.orange,
              borderWidth: 2,
              ...styles.field,
            }}
            titleStyle={ButtonStyle.orangeText}
            title="Forgot Password?"
          />
        </ScrollView>
        <View style={!keyboard ? ButtonContainerStyle.bottomItemsContainer : styles.field}>
          <Button
            disabled={!this.isSubmitEnabled()}
            loading={this.state.loading}
            onPress={() => {
              this.submitLogin();
            }}
            buttonStyle={{
              ...ButtonStyle.primary,
              height: 60,
            }}
            title="Login"
          />
        </View>
      </SafeAreaView>
    );
  }
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  field: {
    marginTop: 20,
    marginBottom: 20,
  },
});
