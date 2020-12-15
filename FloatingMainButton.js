import {KeyboardAvoidingView, View} from "react-native";
import {Button} from "react-native-elements";
import React from "react";
import {useSafeArea} from "react-native-safe-area-context";

import {ButtonStyle} from "./Styles";

const FloatingMainButton = ({isEnabled, isLoading, onPress, title}) => {
    const insets = useSafeArea();
    const keyboardOffset = 64 + insets.bottom * 0.7; // https://github.com/facebook/react-native/issues/13393

    return (
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'position' : null} keyboardVerticalOffset={keyboardOffset}>
            <View>
                <Button
                    disabled={!isEnabled}
                    loading={isLoading}
                    onPress={onPress}
                    buttonStyle={{
                        ...ButtonStyle.primary,
                        height: 60,
                    }}
                    title={title}
                />
            </View>
        </KeyboardAvoidingView>
    )
}

export default FloatingMainButton
