import React from "react";
import {
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  StyleSheet,
  Text,
  TouchableOpacity,
  ScrollView,
  View
} from "react-native";
import LogoTitle from "./LogoTitle";
import { api } from "./Api";
import { ErrorStyle, ButtonContainerStyle, ButtonStyle } from "./Styles";
import { Badge, Button, CheckBox, Input, Icon } from "react-native-elements";

export default class Register extends React.Component {
  static navigationOptions = {
    headerTitle: <LogoTitle title="REGISTER" />,
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
      email: "",
      firstName: "",
      lastName: "",
      password: "",
      phone: "",
      error: false,
      testify: false,
      registering: false
    };
  }

  onEmailChange(email) {
    this.setState({ email });
  }

  onFirstNameChange(firstName) {
    this.setState({ firstName });
  }

  onLastNameChange(lastName) {
    this.setState({ lastName });
  }

  onAlreadyRegistered() {
    this.props.navigation.replace("Login");
  }

  onFirstNameBlur() {
    let firstNameError = null;
    if (this.state.firstName.length == 0) {
      firstNameError = "First Name required";
    }
    this.setState({ firstNameError });
  }

  onLastNameBlur() {
    let lastNameError = null;
    if (this.state.lastName.length == 0) {
      lastNameError = "Last Name required";
    }
    this.setState({ lastNameError });
  }

  onPhoneChange(phone) {
    this.setState({ phone });
  }

  onPhoneBlur() {
    const phone = this.state.phone.replace(/\D/g, "");
    let phoneError = null;
    if (phone.length == 0) {
      phoneError = "Phone required";
    } else if (phone.length !== 10) {
      phoneError = "Phone invalid";
    } else {
      phoneError = "";
    }
    this.setState({
      phoneError
    });
  }

  onEmailBlur(onlySuccess) {
    let emailError = null;
    if (this.state.email.length != 0 && !this.validateEmail(this.state.email)) {
      emailError = "Invalid Email";
    } else {
      emailError = null;
    }
    if (this.state.emailError || !onlySuccess) {
      this.setState({ emailError });
    }
  }

  validateEmail(email) {
    var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
  }

  submit() {
    if (this.state.firstName.length < 1) {
      return Alert.alert(
        "First Name Required",
        "Name required to comply with 311 requirements."
      );
    } else if (this.state.lastName.length < 1) {
      return Alert.alert(
        "Last Name Required",
        "Name required to comply with 311 requirements."
      );
    } else if (this.state.phone.replace(/\D/g, "").length != 10) {
      return Alert.alert(
        "Phone number invalid",
        "Ten digit phone number required."
      );
    } else if (!this.validateEmail(this.state.email)) {
      return Alert.alert(
        "Email invalid",
        "Valid Email required to communicate with 311."
      );
    } else if (!this.state.testify) {
      return Alert.alert(
        "Testify required",
        "You must be willing to testify by phone to use Reported."
      );
    }
    this.setState({ registering: true });
    const { firstName, lastName, phone, testify, email, password } = this.state;
    api
      .register({
        firstName,
        lastName,
        phone: `(${phone.substring(0, 3)}) ${phone.substring(
          3,
          6
        )}-${phone.substring(6, 10)}`,
        testify,
        email,
        password
      })
      .then(_success => {
        const {
          navigation: { navigate }
        } = this.props;
        this.setState({ registering: false });
        navigate("Home");
      })
      .catch(e => {
        const { response: res } = e;
        this.setState({ registering: false });
        const errorMessage = {
          401: "Credentials not found",
          422: "Could not process information"
        }[res.status] || "Unknown error";
        this.setState({ error: errorMessage });
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

  render() {
    return (
      <KeyboardAvoidingView behavior="padding" style={styles.container}>
        <ScrollView>
          <TouchableOpacity
            style={styles.alreadyRegistered}
            onPress={() => this.onAlreadyRegistered()}
          >
            <Text>Already Registered? Login</Text>
          </TouchableOpacity>
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
            label="First Name"
            ref={this.firstName}
            containerStyle={styles.field}
            errorStyle={ErrorStyle.style}
            errorMessage={this.state.firstNameError}
            onChangeText={firstName => this.onFirstNameChange(firstName)}
            onBlur={() => this.onFirstNameBlur()}
            leftIcon={<Icon type="material" name="person" />}
          />
          <Input
            label="Last Name"
            ref={this.lastName}
            containerStyle={styles.field}
            errorStyle={ErrorStyle.style}
            errorMessage={this.state.lastNameError}
            onChangeText={lastName => this.onLastNameChange(lastName)}
            onBlur={() => this.onLastNameBlur()}
            leftIcon={<Icon type="material" name="person" />}
          />
          <Input
            label="Phone"
            keyboardType="phone-pad"
            ref={this.phone}
            containerStyle={styles.field}
            errorStyle={ErrorStyle.style}
            errorMessage={this.state.phoneError}
            onChangeText={email => this.onPhoneChange(email)}
            onBlur={() => this.onPhoneBlur()}
            leftIcon={<Icon type="material-community" name="phone" />}
          />
          <Input
            label="Email"
            autoCapitalize="none"
            keyboardType="email-address"
            ref={this.field}
            containerStyle={styles.field}
            errorStyle={ErrorStyle.style}
            errorMessage={this.state.emailError}
            onChangeText={email => {
              this.onEmailChange(email);
              this.onEmailBlur(true);
            }}
            onBlur={() => this.onEmailBlur(false)}
            leftIcon={<Icon type="material-community" name="email" />}
          />
          <Input
            label="Password (optional)"
            secureTextEntry
            containerStyle={styles.field}
            onChangeText={password => this.setState({ password })}
            errorStyle={ErrorStyle.style}
            leftIcon={<Icon type="material-community" name="lock" />}
          />

          <CheckBox
            containerStyle={styles.field}
            onPress={() => this.setState({ testify: !this.state.testify })}
            title={`I'm willing to testify at a hearing, which can be done by phone. I allow reported to use my images, locations, and descriptions publicly except when explicitly noted for private use.\n\nNote: The majority of complaints do not require a hearing. I understand that the information I submit in a report will be submitted to 311 via webform. I understand that my personal information is required to submit a complaint or compliment to 311.`}
            checked={this.state.testify}
          />
        </ScrollView>
        <View>
          <Button
            onPress={() => this.submit()}
            loading={this.state.registering}
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
            title="Register"
          />
        </View>
      </KeyboardAvoidingView>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1
  },
  email: {
    top: "20%"
  },
  field: {
    marginTop: 30
  },
  alreadyRegistered: {
    marginTop: 10,
    padding: 5,
    alignItems: "center"
  }
});
