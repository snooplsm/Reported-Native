import React from "react";
import {
  TouchableOpacity,
  TouchableNativeFeedback,
  Platform,
  type TouchableNativeFeedbackProps,
  type TouchableOpacityProps
} from "react-native";

type TouchSpoofProps = React.PropsWithChildren<
  TouchableOpacityProps & TouchableNativeFeedbackProps
>;

export default class TouchSpoof extends React.Component<TouchSpoofProps> {
  private ios: boolean;

  constructor(props: TouchSpoofProps) {
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
