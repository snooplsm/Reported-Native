import React from "react";
import { Platform } from "react-native";
import { FlatList, SectionList } from "react-navigation";

export default class ListSpoof extends React.Component {
  constructor(props) {
    super(props);
    this.ios = Platform.OS === "ios";
  }

  render() {
    const { ...props } = this.props;
    if (this.ios) {
      return <SectionList {...props} />;
    } else {
      return <FlatList {...props} />;
    }
  }
}
