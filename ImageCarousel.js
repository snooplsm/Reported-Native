import React from "react";
import { Text, TouchableOpacity, View, StyleSheet } from "react-native";
import { Modal } from "react-native";
import ImageViewer from "react-native-image-zoom-viewer";
import Carousel, { Pagination } from "react-native-snap-carousel";
import SliderEntry from "./SliderEntry";
import { sliderWidth, itemWidth } from "./SliderEntry.style";
import { ImageManipulator } from "expo";

export default class ImageCarousel extends React.Component {
  constructor(props) {
    super(props);
    this.state = {};
    this._carousel = React.createRef();
  }

  _renderItem({ item, index }) {
    return (
      <SliderEntry
        onItemPress={() => {
          console.log(item, index);
          if (this.props.onItemPressed) {
            this.props.onItemPressed({ item, index });
          }
        }}
        key={item.url}
        data={item}
        even={(index + 1) % 2 === 0}
      />
    );
  }

  get pagination() {
    const { entries } = this.props;
    const { activeSlide } = this.state;
    return (
      <Pagination
        dotsLength={entries.length}
        activeDotIndex={activeSlide ?? 0}
        carouselRef={this._carousel}
        containerStyle={{ backgroundColor: "rgba(255, 255, 255, 1)" }}
        dotStyle={{
          width: 10,
          height: 10,
          borderRadius: 5,
          marginHorizontal: 8,
          backgroundColor: "rgba(0, 0, 0, 0.72)"
        }}
        tappableDots={true}
        inactiveDotStyle={
          {
            // Define styles for inactive dots here
          }
        }
        inactiveDotOpacity={0.4}
        inactiveDotScale={0.6}
      />
    );
  }

  render() {
    return (
      <View>
        <Carousel
          ref={this._carousel}
          data={this.props.entries.map(x => {
            return {
              illustration: x.url,
              title: "",
              subtitle: ""
            };
          })}
          onSnapToItem={index => this.setState({ activeSlide: index })}
          renderItem={this._renderItem.bind(this)}
          sliderWidth={sliderWidth}
          itemWidth={itemWidth}
          onPress={() => {
            console.log("onpresszi");
          }}
        />
        {this.pagination}
      </View>
    );
  }
}
