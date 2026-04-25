import { KeyboardAvoidingView, View, Platform, type StyleProp, type ViewStyle } from "react-native";
import { Button } from "react-native-elements";
import React from "react";

import { ButtonStyle } from "./Styles";

type FloatingMainButtonProps = {
    isEnabled?: boolean;
    isLoading?: boolean;
    onPress?: () => void;
    title: string;
    containerStyle?: StyleProp<ViewStyle>;
};

const FloatingMainButton = ({ isEnabled = true, isLoading, onPress, title, containerStyle = {} }: FloatingMainButtonProps) => {
    // const insets = useSafeArea();
    // const keyboardOffset = 64 + insets.bottom * 0.7; // https://github.com/facebook/react-native/issues/13393

    return (
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'position' : undefined}
        // keyboardVerticalOffset={keyboardOffset}
        >
            <View style={containerStyle}>
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
