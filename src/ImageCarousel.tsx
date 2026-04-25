import React from "react";
import { View } from "react-native";
import Carousel, { Pagination } from "react-native-snap-carousel";
import SliderEntry from "./SliderEntry";
import { sliderWidth, itemWidth } from "./SliderEntry.style";

type ImageCarouselEntry = {
  url: string;
};

type SliderData = {
  url: string;
  illustration: string;
  title: string;
  subtitle: string;
};

type ImageCarouselProps = {
  entries?: ImageCarouselEntry[];
  onItemPressed?: (args: { item: SliderData; index: number }) => void;
};

type ImageCarouselState = {
  activeSlide?: number;
};

const TypedCarousel = Carousel as unknown as React.ComponentType<any>;
const TypedPagination = Pagination as unknown as React.ComponentType<any>;

export default class ImageCarousel extends React.Component<ImageCarouselProps, ImageCarouselState> {
  private _carousel: React.RefObject<unknown>;

  constructor(props: ImageCarouselProps) {
    super(props);
    this.state = {};
    this._carousel = React.createRef();
  }

  _renderItem({ item, index }: { item: SliderData; index: number }) {
    return (
      <SliderEntry
        onItemPress={() => {
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
    if (!entries) {
      return null;
    }

    return (
      <TypedPagination
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
    if (!this.props.entries) {
      return <></>;
    }

    return (
      <View>
        <TypedCarousel
          ref={this._carousel}
          data={this.props.entries.map(x => {
            return {
              url: x.url,
              illustration: x.url,
              title: "",
              subtitle: ""
            };
          })}
          onSnapToItem={index => this.setState({ activeSlide: index })}
          renderItem={this._renderItem.bind(this)}
          sliderWidth={sliderWidth}
          itemWidth={itemWidth}
        />
        {this.pagination}
      </View>
    );
  }
}
