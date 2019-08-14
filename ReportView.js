import React from "react";

import {
  Image,
  ImageBackground,
  StyleSheet,
  Linking,
  Text,
  View,
  TouchableOpacity
} from "react-native";
import { Card, Icon } from "react-native-elements";
import Autolink from "react-native-autolink";
import { HorizontalStyle, ButtonStyle } from "./Styles";
import ImageCarousel from "./ImageCarousel";
import moment from "moment";

const reduce512 = (prev, curr) => {
  return Math.abs(curr.width - 512) < Math.abs(prev.width - 512) ? curr : prev;
};

export default class ReportView extends React.Component {
  constructor(props) {
    super(props);
  }

  carousel() {
    const { report: rpt } = this.props;
    const { report, address } = rpt;
    if (report.media && report.media.length > 0) {
      return report.media.map((image, index) => {
        const thumb = image.thumbnails.reduce(reduce512);
        return (
          <>
            <TouchableOpacity onPress={() => Linking.openURL(image.url)}>
              <ImageBackground
                source={{ uri: thumb.url }}
                style={{
                  width: "100%",
                  height: 256,
                  resizeMode: "cover"
                }}
              />
              {image.type === "YOUTUBE" && (
                <View
                  isVisible={
                    image.type === "YOUTUBE" || image.type === "S3_VIDEO"
                  }
                  style={{
                    position: "absolute",
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    justifyContent: "center",
                    alignItems: "center"
                  }}
                >
                  <View
                    style={{
                      position: "absolute",
                      width: 80,
                      height: 80,
                      borderRadius: 80 / 2,
                      backgroundColor: "#FFFFFF99"
                    }}
                  />
                  <Icon name="play-circle-outline" type="material" size={100} />
                </View>
              )}
            </TouchableOpacity>

            {index !== report.media.length - 1 && (
              <View style={{ height: 10, paddingTop: 10 }}></View>
            )}
          </>
        );
      });
    } else {
      return <></>;
    }
  }

  closeModal() {
    this.setState({ imageModalImages: false });
  }

  imageModal() {
    if (this.state.imageModalImages) {
      return (
        <Modal visible={true} onRequestClose={() => {}} transparent={false}>
          <ImageViewer
            onClick={() => this.closeModal()}
            imageUrls={this.state.imageModalImages}
          />
        </Modal>
      );
    } else {
      return <></>;
    }
  }

  get status() {
    const { report: rpt } = this.props;
    const { report, address } = rpt;
    if (report.status <= 0) {
      return (
        <View>
          <Text>[PENDING]</Text>
        </View>
      );
    }
  }
  render() {
    const { report: rpt } = this.props;
    console.log(report);
    const { report, address } = rpt;
    const time = moment(report.timeofincident);
    return (
      <Card title={report.complaint}>
        <View
          style={[
            HorizontalStyle.style,
            {
              paddingBottom: 10
            }
          ]}
        >
          <Text>
            {address.building} {address.street}
          </Text>
          <Text style={ButtonStyle.orangeText}>{report.license.plate}</Text>
        </View>
        {this.carousel()}
        <Autolink text={report.description ?? ""} />
        <Autolink text={report.notes ?? ""} />
        <View
          style={[
            HorizontalStyle.style,
            {
              paddingTop: 10
            }
          ]}
        >
          <Text>{` ${time.fromNow()} \n ${time.format(
            "M/D/YY h:mm A"
          )} `}</Text>
          <Text style={ButtonStyle.orangeText}>
            {address.cb ? `#CB${address.cb}` : ""}
          </Text>
        </View>
      </Card>
    );
  }
}
