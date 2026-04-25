import React from "react";
import {
  Text,
  View,
} from "react-native";

type LogoTitleProps = {
  title?: string;
};

export default class Submission extends React.Component<LogoTitleProps> {
  constructor(props: LogoTitleProps) {
    super(props);
  }

  render() {
    return (
      <View
        style={{
          position: "absolute",
          flex: 1,
          width: "100%",
          justifyContent: "center",
          alignItems: "center"
        }}
      >
        <Text
          style={{
            fontSize: 20,
            fontWeight: "bold",
            letterSpacing: 1.0
          }}
        >
          {this.props.title || "REPORTED"}
        </Text>
      </View>
    );
  }
}
