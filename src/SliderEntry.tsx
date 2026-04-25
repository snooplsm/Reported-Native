import React, { Component } from "react";
import { View, Text, Image, TouchableOpacity } from "react-native";
import PropTypes from "prop-types";
import { ParallaxImage } from "react-native-snap-carousel";
import styles from "./SliderEntry.style";

type SliderEntryData = {
  illustration: string;
  title?: string;
  subtitle?: string;
};

type SliderEntryProps = {
  data: SliderEntryData;
  even?: boolean;
  parallax?: boolean;
  parallaxProps?: Record<string, unknown>;
  onItemPress?: () => void;
};

const TypedParallaxImage = ParallaxImage as React.ComponentType<any>;

export default class SliderEntry extends Component<SliderEntryProps> {
  static propTypes = {
    data: PropTypes.object.isRequired,
    even: PropTypes.bool,
    parallax: PropTypes.bool,
    parallaxProps: PropTypes.object
  };

  get image() {
    const {
      data: { illustration },
      parallax,
      parallaxProps,
      even
    } = this.props;
    return parallax ? (
      <TypedParallaxImage
        source={{ uri: illustration }}
        containerStyle={[
          styles.imageContainer,
          even ? styles.imageContainerEven : {}
        ]}
        style={styles.image}
        parallaxFactor={0.35}
        showSpinner={true}
        spinnerColor={even ? "rgba(255, 255, 255, 0.4)" : "rgba(0, 0, 0, 0.25)"}
        {...parallaxProps}
      />
    ) : (
      <Image
        source={{ uri: illustration }}
        style={styles.image}
        {...parallaxProps}
      />
    );
  }

  render() {
    const {
      data: { title, subtitle },
      even
    } = this.props;
    return (
      <TouchableOpacity
        onPress={() => this.props.onItemPress?.()}
        activeOpacity={1}
        style={styles.slideInnerContainer}
      >
        <View style={styles.shadow} />
        <View
          style={[styles.imageContainer, even ? styles.imageContainerEven : {}]}
        >
          {this.image}
          <View
            style={[styles.radiusMask, even ? styles.radiusMaskEven : {}]}
          />
        </View>
      </TouchableOpacity>
    );
  }
}
