import React from 'react';
import {Text, TouchableOpacity, View, StyleSheet} from 'react-native';
import { Modal } from 'react-native';
import ImageViewer from 'react-native-image-zoom-viewer'
import Carousel, { Pagination } from 'react-native-snap-carousel';
import SliderEntry from './SliderEntry'
import { sliderWidth, itemWidth } from './SliderEntry.style';
import { ImageManipulator } from 'expo';

export default class ImageCarousel extends React.Component {

  constructor(props) {
    super(props)
  }

  _renderItem ({item, index}) {
      return <SliderEntry data={item} even={(index + 1) % 2 === 0} />;
  }

  render() {
    return (
      <View style={{backgroundColor: 'yellow'}}>
      <Carousel
        ref={(c) => { this._carousel = c; }}
        data={this.props.entries.map(x=> {
          return {
          illustration: x.url,
          title: "",
          subtitle: ""
        }})}
        renderItem={this._renderItem}
        sliderWidth={sliderWidth}
        itemWidth={itemWidth}
      />
      </View>
    )
  }
}
