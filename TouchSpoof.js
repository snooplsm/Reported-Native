import React from "react";
import {
  Alert,
  Text,
  TouchableOpacity,
  TouchableNativeFeedback,
  View,
  StyleSheet,
  ScrollView,
  Platform
} from "react-native";

export default class TouchSpoof extends React.Component {
  constructor(props) {
    super(props);
    this.ios = Platform.OS === "ios";
  }

  render() {
    const { children, ...props } = this.props;
    if (this.ios) {
      return <TouchableOpacity {...props}>{children}</TouchableOpacity>;
    } else {
      return (
        <TouchableNativeFeedback {...props}>{children}</TouchableNativeFeedback>
      );
    }
  }
}
