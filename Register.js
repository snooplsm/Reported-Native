import React from "react";
import {
  Alert,
  KeyboardAvoidingView,
  StyleSheet,
  Text,
  TouchableOpacity,
  ScrollView,
  View
} from "react-native";
import { SafeAreaView } from 'react-native-safe-area-context';
import LogoTitle from "./LogoTitle";
import { api } from "./Api";
import {
  ErrorStyle,
  ButtonStyle,
  globalStyles,
} from "./Styles";
import FloatingMainButton from "./FloatingMainButton";
import { Badge, Button, CheckBox, Input, Icon } from "react-native-elements";
import {KeyboardAwareScrollView} from "react-native-keyboard-aware-scroll-view";

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

  onFieldChange(fieldName, val) {
    this.setState({ [fieldName]: val })
  }

  onAlreadyRegistered() {
    this.props.navigation.replace("Login");
  }

  onFirstNameBlur() {
    const { firstName } = this.state;
    this.setState({
      firstNameError: firstName.length == 0 ? "First Name required" : null
    });
  }

  onLastNameBlur() {
    const { lastName } = this.state;
    this.setState({
      lastNameError: lastName.length == 0 ? "Last Name required" : null
    });
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
    const { firstName, lastName, phone, testify, email, password } = this.state;
    if (firstName.length < 1) {
      return Alert.alert(
        "First Name Required",
        "Name required to comply with 311 requirements."
      );
    } else if (lastName.length < 1) {
      return Alert.alert(
        "Last Name Required",
        "Name required to comply with 311 requirements."
      );
    } else if (phone.replace(/\D/g, "").length != 10) {
      return Alert.alert(
        "Phone number invalid",
        "Ten digit phone number required."
      );
    } else if (!this.validateEmail(email)) {
      return Alert.alert(
        "Email invalid",
        "Valid Email required to communicate with 311."
      );
    } else if (!testify) {
      return Alert.alert(
        "Testify required",
        "You must be willing to testify by phone to use Reported."
      );
    }
    this.setState({ registering: true });

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

  render() {
    const { registering } = this.state;
    return (
      <SafeAreaView style={styles.mainWrapper}>
        <KeyboardAwareScrollView style={globalStyles.mainContainer}>
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
          <View style={styles.inputWrapper}>
            <Input
              label="First Name"
              ref={this.firstName}
              containerStyle={styles.field}
              errorStyle={ErrorStyle.style}
              errorMessage={this.state.firstNameError}
              onChangeText={firstName => this.onFieldChange('firstName', firstName)}
              onBlur={() => this.onFirstNameBlur()}
              leftIcon={<Icon type="material" name="person" />}
            />
          </View>
          <View style={styles.inputWrapper}>
            <Input
              label="Last Name"
              ref={this.lastName}
              containerStyle={styles.field}
              errorStyle={ErrorStyle.style}
              errorMessage={this.state.lastNameError}
              onChangeText={lastName => this.onFieldChange('lastName', lastName)}
              onBlur={() => this.onLastNameBlur()}
              leftIcon={<Icon type="material" name="person" />}
            />
          </View>
          <View style={styles.inputWrapper}>
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
          </View>
          <View style={styles.inputWrapper}>
            <Input
              label="Email"
              autoCapitalize="none"
              keyboardType="email-address"
              ref={this.field}
              containerStyle={styles.field}
              errorStyle={ErrorStyle.style}
              errorMessage={this.state.emailError}
              onChangeText={email => {
                this.onFieldChange('email', email);
                this.onEmailBlur(true);
              }}
              onBlur={() => this.onEmailBlur(false)}
              leftIcon={<Icon type="material-community" name="email" />}
            />
          </View>
          <View style={styles.inputWrapper}>
            <Input
              label="Password (optional)"
              secureTextEntry
              containerStyle={styles.field}
              onChangeText={password => this.setState({ password })}
              errorStyle={ErrorStyle.style}
              leftIcon={<Icon type="material-community" name="lock" />}
            />
          </View>
          <View style={styles.inputWrapper}>
            <CheckBox
              containerStyle={styles.field}
              onPress={() => this.setState({ testify: !this.state.testify })}
              title={`I'm willing to testify at a hearing, which can be done by phone. I allow reported to use my images, locations, and descriptions publicly except when explicitly noted for private use.\n\nNote: The majority of complaints do not require a hearing. I understand that the information I submit in a report will be submitted to 311 via webform. I understand that my personal information is required to submit a complaint or compliment to 311.`}
              checked={this.state.testify}
            />
          </View>
        </KeyboardAwareScrollView>
        <FloatingMainButton
          isLoading={registering}
          onPress={() => this.submit()}
          title={'Register'}
          containerStyle={styles.registerButton}
        />
      </SafeAreaView>
    );
  }
}

const styles = StyleSheet.create({
  email: {
    top: "20%"
  },
  mainWrapper: {
    flex: 1,
  },
  field: {
    marginTop: 30
  },
  inputWrapper: {
    marginLeft: -8,
    marginRight: -8,
  },
  alreadyRegistered: {
    marginTop: 10,
    padding: 5,
    alignItems: "center"
  },
  registerButton: {
    paddingHorizontal: 10,
    paddingVertical: 5
  }
});
