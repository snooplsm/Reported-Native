import React from "react";

import {
    Alert,
    StyleSheet,
    Text,
    TouchableOpacity,
} from "react-native";
import { useNavigation } from '@react-navigation/native';
import { Input, Icon } from "react-native-elements";
import { ErrorStyle, globalStyles } from "./Styles";
import { api } from "./Api";
import FloatingMainButton from "./FloatingMainButton";
import { KeyboardAwareScrollView } from "react-native-keyboard-aware-scroll-view";
import { useAuth } from "./AuthProvider";

type UserProfile = {
    email: string;
    phone: string;
    firstName: string;
    lastName: string;
    [key: string]: unknown;
};

type ProfileErrors = {
    firstNameError?: string | null;
    lastNameError?: string | null;
    phoneError?: string | null;
    emailError?: string | null;
};

const emptyUserData: UserProfile = {
    email: "",
    phone: "",
    firstName: "",
    lastName: "",
};

export default function Profile() {
    const [editable, setEditable] = React.useState(false);
    const [userData, setUserData] = React.useState<UserProfile>(emptyUserData);
    const [errors, setErrors] = React.useState<ProfileErrors>({});

    const navigation = useNavigation<any>();
    const auth = useAuth();

    const logoutPressed = () => {
        auth.logout().then((result) => {
            console.log('sign out', result);
        });
    };

    const editPressed = () => {
        setEditable(!editable);
        if (editable) {
            // this.setData(user);
        }
        const editMessage = editable ? "EDIT" : "CANCEL";
        navigation.setParams({
            editText: editMessage
        });
    };

    const setData = (user: UserProfile) => {
        setUserData(user);
    }

    const onChange = (field: keyof UserProfile, value: string) => {
        const user = { ...userData };
        user[field] = value;
        setUserData(user);
    }

    const onFirstNameBlur = () => {
        let firstNameError = null;
        if (!userData.firstName.length) {
            firstNameError = "First Name required";
        } else {
            firstNameError = null;
        }
        setErrors({ ...errors, ...{ firstNameError } });
    }

    const onLastNameBlur = () => {
        let lastNameError = null;
        if (!userData.lastName.length) {
            lastNameError = "First Name required";
        } else {
            lastNameError = null;
        }
        setErrors({ ...errors, ...{ lastNameError } });
    }

    const onPhoneBlur = () => {
        const phone = userData.phone.replace(/\D/g, "");
        let phoneError = "";
        if (!phone.length) {
            phoneError = "Phone required";
        } else if (phone.length !== 10) {
            phoneError = "Phone invalid";
        } else {
            phoneError = "";
        }
        setErrors({ ...errors, ...{ phoneError } });
    }

    const onEmailBlur = () => {
        let emailError = null;
        if (!!userData.email.length && !validateEmail(userData.email)) {
            emailError = "Invalid Email";
        } else {
            emailError = null;
        }
        setErrors({ ...errors, ...{ emailError } });
    }

    const validateEmail = (email: string) => {
        const re = /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
        return re.test(email);
    }

    const updateUser = () => {
        const { email, phone, firstName, lastName } = userData;
        const newUser = { email, phone, firstName, lastName };
        api
            .updateUser(newUser)
            .then((success: any) => success.data)
            .then((success: UserProfile) => {
                setData(success);
                editPressed();
            })
            .catch(() => {
                Alert.alert("Problem", "Could not update.");
            });
    }

    React.useEffect(() => {
        const user = auth.userObj;
        if (!!user) {
            setUserData(user);
        } else {
            logoutPressed();
        }
        navigation.setParams({
            logoutPressed: logoutPressed,
            editPressed: editPressed,
            editText: "EDIT",
        });
    }, []);

    return (
        <>
            {!!userData &&
                <KeyboardAwareScrollView style={globalStyles.mainContainer} viewIsInsideTabBar>
                    <Input
                        label={"First Name"}
                        editable={editable}
                        containerStyle={styles.field}
                        value={userData.firstName}
                        errorStyle={ErrorStyle.style}
                        errorMessage={errors.firstNameError}
                        onChangeText={(value) => onChange('firstName', value)}
                        onBlur={() => onFirstNameBlur()}
                        leftIcon={<Icon type="material" name="person" />}
                    />
                    <Input
                        label={"Last Name"}
                        value={userData.lastName}
                        editable={editable}
                        containerStyle={styles.field}
                        errorStyle={ErrorStyle.style}
                        errorMessage={errors.lastNameError}
                        onChangeText={(value) => onChange('lastName', value)}
                        onBlur={() => onLastNameBlur()}
                        leftIcon={<Icon type="material" name="person" />}
                    />
                    <Input
                        label={"Phone"}
                        keyboardType={"phone-pad"}
                        editable={editable}
                        value={userData.phone}
                        containerStyle={styles.field}
                        errorStyle={ErrorStyle.style}
                        errorMessage={errors.phoneError}
                        onChangeText={(value) => onChange('phone', value)}
                        onBlur={() => onPhoneBlur()}
                        leftIcon={<Icon type="material-community" name="phone" />}
                    />
                    <Input
                        label={"Email"}
                        autoCapitalize={"none"}
                        keyboardType="email-address"
                        editable={editable}
                        value={userData.email}
                        containerStyle={styles.field}
                        errorStyle={ErrorStyle.style}
                        errorMessage={errors.emailError}
                        onChangeText={(value) => onChange('email', value)}
                        onBlur={() => onEmailBlur()}
                        leftIcon={<Icon type="material-community" name="email" />}
                    />
                    <TouchableOpacity style={styles.logoutButton} onPress={logoutPressed}>
                        <Text style={styles.logoutButtonText}>Logout</Text>
                    </TouchableOpacity>
                </KeyboardAwareScrollView>
            }
            {
                editable &&
                <FloatingMainButton
                    isEnabled
                    onPress={() => updateUser()}
                    title={'Save'}
                    containerStyle={styles.saveButton}
                />
            }
        </>
    );
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
    },
    saveButton: {
        paddingHorizontal: 10,
        paddingVertical: 5
    },
    mainWrapper: {
        flex: 1,
    },
    logoutButton: {
        marginTop: 50,
        alignItems: 'center',
    },
    logoutButtonText: {
        fontSize: 20,
    },
});
