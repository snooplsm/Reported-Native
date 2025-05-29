import React from "react";
import { Platform } from "react-native";
import { SwipeListView } from "react-native-swipe-list-view";

export default class ListSpoof extends React.Component {
  render() {
    const { ...props } = this.props;
    if (Platform.OS === "ios") {
      return <SwipeListView useSectionList={true} {...props} />;
    } else {
      return <SwipeListView useSectionList={false} {...props} />;
    }
  }
}
