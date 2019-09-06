import React from "react";

import {
  Alert,
  StyleSheet,
  Text,
  View,
  SafeAreaView,
  TouchableOpacity,
  KeyboardAvoidingView
} from "react-native";
import { Button, Input, Icon } from "react-native-elements";
import LogoTitle from "./LogoTitle";
import { ErrorStyle, ButtonContainerStyle, ButtonStyle } from "./Styles";
import { api } from "./Api";
import { signOut, isSignedIn } from "./Auth";

export default class Profile extends React.Component {
  static navigationOptions = ({ navigation }) => {
    return {
      headerTitle: <LogoTitle title={"PROFILE"} />,
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
    this.setState({ email });
  }

  onFirstNameChange(firstName) {
    this.setState({ firstName });
  }

  onLastNameChange(lastName) {
    this.setState({ lastName });
  }

  onFirstNameBlur() {
    let firstNameError = null;
    if (this.state.firstName.length == 0) {
      firstNameError = "First Name required";
    } else {
      firstNameError = null;
    }
    this.setState({ firstNameError });
  }

  onLastNameBlur() {
    let lastNameError = null;
    if (this.state.lastName.length == 0) {
      lastNameError = "First Name required";
    } else {
      lastNameError = null;
    }
    this.setState({ lastNameError });
  }

  onPhoneChange(phone) {
    this.setState({ phone });
  }

  onPhoneBlur() {
    const state = this.state;
    const phone = this.state.phone.replace(/\D/g, "");
    let phoneError = "";
    if (phone.length == 0) {
      phoneError = "Phone required";
    } else if (phone.length !== 10) {
      phoneError = "Phone invalid";
    } else {
      phoneError = "";
    }
    this.setState({ phoneError });
  }

  onLastNameBlur() {
    const state = this.state;
    let lastNameError = null;
    if (this.state.lastName.length == 0) {
      lastNameError = "Last Name required";
    } else {
      lastNameError = null;
    }
    this.setState({ lastNameError });
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

  updateUser() {
    const user = {};
    const { email, phone, firstName, lastName } = this.state;
    const { email: e, phone: p, firstName: f, lastName: l } = this.state.user;
    const newUser = { email, phone, firstName, lastName };
    api
      .updateUser(newUser)
      .then(success => success.data)
      .then(success => {
        this.setData(success);
        this.editPressed();
      })
      .catch(e => {
        Alert.alert("Problem", "Could not update.");
      });
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
            opacity: this.state.editable ? 100 : 0,
            width: "100%",
            position: "absolute"
          }}
          buttonStyle={{
            padding: 20
          }}
          title="Save"
          onPress={() => {
            this.updateUser();
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
