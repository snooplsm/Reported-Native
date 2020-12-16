import React from "react";
import {
  Alert,
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
} from "./Styles";
import { SafeAreaView } from 'react-native-safe-area-context';
import LogoTitle from "./LogoTitle";
import { Badge, Button, Input, Icon } from "react-native-elements";
import { api } from "./Api";
import FloatingMainButton from "./FloatingMainButton";

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
          message = "Unknown error";
        }
        this.setState({ loading: false, error: message });
      });
  }

  isSubmitEnabled() {
    return (
      this.validateEmail(this.state.email) && this.state.password.length > 2
    );
  }

  validateEmail(email) {
    const re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
  }

  render() {
    const { error, loading } = this.state

    return (
      <SafeAreaView style={globalStyles.flex1}>
        <ScrollView style={globalStyles.mainContainer}>
          <View
            style={{
              opacity: error ? 100 : 0,
              justifyContent: "center",
              alignItems: "center",
              flexDirection: "row"
            }}
          >
            <Badge status="error" />
            <Text> {error ?? "TAKE UP SPACE"}</Text>
          </View>
          <View style={styles.inputWrapper}>
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
          </View>
          <View style={styles.inputWrapper}>
            <Input
              label="Password"
              secureTextEntry={true}
              containerStyle={{ ...styles.field, ...styles.inputFix }}
              errorStyle={ErrorStyle.style}
              onChangeText={password => this.setState({ password })}
            />
          </View>
          <Button
            loading={loading}
            backgroundColor={'white' }
            onPress={() => this.onForgotPassword()}
            buttonStyle={{
              ...ButtonStyle.outline,
              height: 60,
            }}
            containerStyle={{
              ...styles.field,
            }}
            titleStyle={ButtonStyle.orangeText}
            title="Forgot Password?"
          />
        </ScrollView>
        <FloatingMainButton
          isEnabled={this.isSubmitEnabled()}
          isLoading={loading}
          onPress={() => this.submitLogin()}
          title={'Login'}
          containerStyle={styles.loginButtonWrapper}
        />
      </SafeAreaView>
    );
  }
}

const styles = StyleSheet.create({
  field: {
    marginTop: 20,
    marginBottom: 20,
  },
  inputWrapper: {
    marginLeft: -8,
    marginRight: -8,
  },
  loginButtonWrapper: {
    paddingHorizontal: 10,
    paddingVertical: 5
  }
});
