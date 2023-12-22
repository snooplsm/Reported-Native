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
import { useNavigation } from '@react-navigation/native';

import LogoTitle from "./LogoTitle";
import { Badge, Button, Input, Icon } from "react-native-elements";
import { api } from "./Api";
import FloatingMainButton from "./FloatingMainButton";
import { signIn } from "./Auth";
import { useAuth } from "./AuthProvider";

export default function Login() {
    const [error, setError] = React.useState(undefined);
    const [emailError, setEmailError] = React.useState(undefined);
    const [email, setEmail] = React.useState('');
    const [password, setPassword] = React.useState('');
    const [loading, setLoading] = React.useState(false);

    const navigation = useNavigation();
    const auth = useAuth();

    const navigationOptions = {
        headerTitle: <LogoTitle title="LOGIN" />,
        headerRight: (
            <Icon
                containerStyle={{ padding: 10, opacity: 0 }}
                name="arrow-back"
                color="#000"
            />
        )
    };

    /*
    constructor(props) {
        super(props);
        this.email = React.createRef();
        this.state = {
            error: undefined,
            email: "",
            password: "",
            loading: false
        };

        console.log('login created');
    }
    */

    const onEmailChange = (email) => {
        setEmail(email);
    }

    const onEmailBlur = () => {
        let myEmailError = null;
        if (email.length != 0 && !validateEmail(email)) {
            myEmailError = "Invalid Email";
        } else {
            myEmailError = null;
        }
        setEmailError(myEmailError);
    }

    const onForgotPassword = () => {
        if (!validateEmail(email)) {
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

    const submitLogin = () => {
        if (loading) {
            return;
        }
        setLoading(true);
        setError(null);
        api
            .login(email, password)
            .then(res => {
                console.log('login', res.data);
                setLoading(false);
                // navigate("Submission");
                // navigation.dispatch(StackActions.replace('SignedIn', { key: 'Submission' }));
                auth.login(res.data)
                    .then(() => {
                        console.log('logged in');
                        // navigation.popToTop();
                        // navigation.replace('SignedIn');
                    })
                    .catch();
            })
            .catch(x => {
                setLoading(false);
                let message = "";
                if (x.response) {
                    if (x.response.status == 401) {
                        message = "Credentials not found";
                    } else if (x.response.status >= 500 && x.response.status < 600) {
                        message = "Server error";
                    } else {
                        message = x.response.data.message || "No response from server";
                    }
                } else if (x.request) {
                    message = "Server was unresponsive";
                } else {
                    message = "Unknown error";
                }
                console.log('login error', message, x.response);
                setError(message);
                setLoading(false);
            });
    }

    const isSubmitEnabled = () => {
        return (
            validateEmail(email) && password.length > 2
        );
    }

    const validateEmail = (myEmail) => {
        const re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
        return re.test(myEmail);
    }

    React.useState(() => {
        auth.logout();
    }, [])

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
                        containerStyle={styles.email}
                        errorStyle={ErrorStyle.style}
                        errorMessage={emailError}
                        onChangeText={email => onEmailChange(email)}
                        onBlur={() => onEmailBlur()}
                    />
                </View>
                <View style={styles.inputWrapper}>
                    <Input
                        label="Password"
                        secureTextEntry={true}
                        containerStyle={{ ...styles.field, ...styles.inputFix }}
                        errorStyle={ErrorStyle.style}
                        onChangeText={password => setPassword(password)}
                    />
                </View>
                <Button
                    loading={loading}
                    backgroundColor={'white'}
                    onPress={() => onForgotPassword()}
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
                isEnabled={isSubmitEnabled()}
                isLoading={loading}
                onPress={() => submitLogin()}
                title={'Login'}
                containerStyle={styles.loginButtonWrapper}
            />
        </SafeAreaView>
    );
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
