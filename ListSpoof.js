import React from "react";
import { Platform } from "react-native";
import { SwipeListView } from "react-native-swipe-list-view";

export default class ListSpoof extends React.Component {
  constructor(props) {
    super(props);
    this.ios = Platform.OS === "ios";
  }

  render() {
    const { ...props } = this.props;
    if (this.ios) {
      return <SwipeListView useSectionList={true} {...props} />;
    } else {
      return <SwipeListView useSectionList={false} {...props} />;
    }
  }
}
