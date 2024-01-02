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
import { KeyboardAwareScrollView } from "react-native-keyboard-aware-scroll-view";
import { useNavigation } from "@react-navigation/native";
import { useAuth } from "./AuthProvider";

export default function Register() {
  const navigationOptions = {
    headerTitle: <LogoTitle title="REGISTER" />,
    headerRight: (
      <Icon
        containerStyle={{ padding: 10, opacity: 0 }}
        name="arrow-back"
        color="#000"
      />
    )
  };

  const email = React.createRef();
  const [stateEmail, setEmail] = React.useState('');
  const [stateFirstName, setFirstName] = React.useState('');
  const [stateLastName, setLastName] = React.useState('');
  const [statePassword, setPassword] = React.useState('');
  const [statePhone, setPhone] = React.useState('');
  const [stateError, setError] = React.useState(false);
  const [stateTestify, setTestify] = React.useState(false);
  const [stateRegistering, setRegistering] = React.useState(false);
  const [firstNameError, setFirstNameError] = React.useState();
  const [lastNameError, setLastNameError] = React.useState();
  const [phoneError, setPhoneError] = React.useState();
  const [emailError, setEmailError] = React.useState();

  const navigation = useNavigation();
  const auth = useAuth();

  const onAlreadyRegistered = () => {
    navigation.replace("Login");
  }

  const onFirstNameBlur = () => {
    setFirstNameError(!stateFirstName.length ? "First Name required" : null);
  }

  const onLastNameBlur = () => {
    setLastNameError(!stateLastName.length ? "Last Name required" : null);
  }

  const onPhoneBlur = () => {
    const phone = statePhone.replace(/\D/g, "");
    let phoneError = null;
    if (phone.length == 0) {
      phoneError = "Phone required";
    } else if (phone.length !== 10) {
      phoneError = "Phone invalid";
    } else {
      phoneError = "";
    }
    setPhoneError(phoneError);
  }

  const onEmailBlur = (onlySuccess) => {
    let emailError = null;
    if (stateEmail.length != 0 && !validateEmail(stateEmail)) {
      emailError = "Invalid Email";
    }

    if (emailError || !onlySuccess) {
      setEmailError(emailError);
    }
  }

  const validateEmail = (email) => {
    var re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
  }

  const submit = () => {
    if (!stateFirstName.length) {
      return Alert.alert(
        "First Name Required",
        "Name required to comply with 311 requirements."
      );
    } else if (!stateLastName.length) {
      return Alert.alert(
        "Last Name Required",
        "Name required to comply with 311 requirements."
      );
    } else if (statePhone.replace(/\D/g, "").length !== 10) {
      return Alert.alert(
        "Phone number invalid",
        "Ten digit phone number required."
      );
    } else if (!validateEmail(stateEmail)) {
      return Alert.alert(
        "Email invalid",
        "Valid Email required to communicate with 311."
      );
    } else if (!stateTestify) {
      return Alert.alert(
        "Testify required",
        "You must be willing to testify by phone to use Reported."
      );
    }
    setRegistering(true);

    api
      .register({
        firstName: stateFirstName,
        lastName: stateLastName,
        phone: `(${statePhone.substring(0, 3)}) ${statePhone.substring(
          3,
          6
        )}-${statePhone.substring(6, 10)}`,
        testify: stateTestify,
        email: stateEmail,
        password: statePassword,
      })
      .then(_success => {
        console.log('register', _success);
        setRegistering(false);
        navigate('Home');
      })
      .catch(e => {
        const { response: res } = e;
        setRegistering(false);
        const errorMessage = {
          401: "Credentials not found",
          422: "Could not process information"
        }[res.status] || "Unknown error";
        setError(errorMessage);
      });
  }

  return (
    <SafeAreaView style={styles.mainWrapper}>
      <KeyboardAwareScrollView style={globalStyles.mainContainer}>
        <TouchableOpacity
          style={styles.alreadyRegistered}
          onPress={() => onAlreadyRegistered()}
        >
          <Text>Already Registered? Login</Text>
        </TouchableOpacity>
        <View
          style={{
            opacity: !!stateError ? 100 : 0,
            justifyContent: "center",
            alignItems: "center",
            flexDirection: "row"
          }}
        >
          <Badge status="error" />
          <Text> {!!stateError ?? "TAKE UP SPACE"}</Text>
        </View>
        <View style={{ marginTop: "10%" }} />
        <View style={styles.inputWrapper}>
          <Input
            label="First Name"
            containerStyle={styles.field}
            errorStyle={ErrorStyle.style}
            errorMessage={firstNameError}
            onChangeText={v => setFirstName(v)}
            onBlur={() => onFirstNameBlur()}
            leftIcon={<Icon type="material" name="person" />}
          />
        </View>
        <View style={styles.inputWrapper}>
          <Input
            label="Last Name"
            containerStyle={styles.field}
            errorStyle={ErrorStyle.style}
            errorMessage={lastNameError}
            onChangeText={v => setLastName(v)}
            onBlur={() => onLastNameBlur()}
            leftIcon={<Icon type="material" name="person" />}
          />
        </View>
        <View style={styles.inputWrapper}>
          <Input
            label="Phone"
            keyboardType="phone-pad"
            containerStyle={styles.field}
            errorStyle={ErrorStyle.style}
            errorMessage={phoneError}
            onChangeText={v => setPhone(v)}
            onBlur={() => onPhoneBlur()}
            leftIcon={<Icon type="material-community" name="phone" />}
          />
        </View>
        <View style={styles.inputWrapper}>
          <Input
            label="Email"
            autoCapitalize="none"
            keyboardType="email-address"
            containerStyle={styles.field}
            errorStyle={ErrorStyle.style}
            errorMessage={emailError}
            onChangeText={v => {
              setEmail(v);
              onEmailBlur(true);
            }}
            onBlur={() => onEmailBlur(false)}
            leftIcon={<Icon type="material-community" name="email" />}
          />
        </View>
        <View style={styles.inputWrapper}>
          <Input
            label="Password (optional)"
            secureTextEntry
            containerStyle={styles.field}
            onChangeText={v => setPassword(v)}
            errorStyle={ErrorStyle.style}
            leftIcon={<Icon type="material-community" name="lock" />}
          />
        </View>
        <View style={styles.inputWrapper}>
          <CheckBox
            containerStyle={styles.field}
            onPress={() => setTestify(!stateTestify)}
            title={`I'm willing to testify at a hearing, which can be done by phone. I allow reported to use my images, locations, and descriptions publicly except when explicitly noted for private use.\n\nNote: The majority of complaints do not require a hearing. I understand that the information I submit in a report will be submitted to 311 via webform. I understand that my personal information is required to submit a complaint or compliment to 311.`}
            checked={stateTestify}
          />
        </View>
      </KeyboardAwareScrollView>
      <FloatingMainButton
        isLoading={stateRegistering}
        onPress={() => submit()}
        title={'Register'}
        containerStyle={styles.registerButton}
      />
    </SafeAreaView>
  );
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
